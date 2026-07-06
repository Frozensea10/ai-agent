package com.agent.mcp.tool;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 代码执行器（沙箱版）。
 *
 * <p>安全提示：本类提供基于 {@code SecurityManager + 自定义 policy} 与正则黑名单的双重防御，
 * 但 Java {@link SecurityManager} 在 JDK 17+ 已被标记为 deprecated，且无法防御所有绕过手段
 * （例如 native 调用、JVM 内部 API、未公开反射等）。<strong>强烈建议：</strong>
 * <ul>
 *   <li>在独立的 Docker 容器内运行本组件，并使用 seccomp profile 限制系统调用
 *       （禁用 fork/exec/socket/open 等）；</li>
 *   <li>使用 Linux namespace 进行 用户 / 网络 / 挂载 / PID 隔离；</li>
 *   <li>以非 root 的低权限系统用户身份运行子进程（配合 {@code --user} 参数）；</li>
 *   <li>通过 cgroups v2 限制 CPU / 内存 / PID 数量；</li>
 *   <li>禁用容器网络（{@code --network none}）以阻断反弹 shell。</li>
 * </ul>
 *
 * <p>本类不应被视为可单独依赖的安全边界，仅作为深度防御的一层。
 * 任何在容器外直接运行本执行器的部署方式都视为存在 RCE 风险。
 */
@Slf4j
@Component
public class JavaCodeExecutor implements BuiltInToolExecutor {

    private static final long TIMEOUT_SECONDS = 30;
    private static final long MAX_OUTPUT_LENGTH = 10000;

    // 危险代码检测模式。
    // 注意：正则黑名单极易被绕过（字符串拼接、Unicode 转义、反射动态加载等），
    // 仅作为第一层防御；最终安全边界由 SecurityManager policy 提供。
    private static final Pattern[] DANGEROUS_PATTERNS = {
        // 进程执行
        Pattern.compile("Runtime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*exec|ProcessBuilder|Process\\s+|\\.exec\\s*\\("),
        // 系统退出 / halt
        Pattern.compile("System\\s*\\.\\s*exit|Runtime\\s*\\.\\s*halt|Runtime\\s*\\.\\s*exit|Runtime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*(exit|halt)"),
        // 文件操作
        Pattern.compile("java\\.io\\.File|java\\.nio\\.file\\.Files|java\\.nio\\.file\\.Paths|FileInputStream|FileOutputStream|RandomAccessFile|FileChannel|FileReader|FileWriter|FileLock"),
        // 网络操作
        Pattern.compile("java\\.net\\.[A-Za-z_]\\w*|javax\\.net\\.[A-Za-z_]\\w*|Socket\\s*\\(|ServerSocket"),
        // 反射和类加载（核心：Class.forName / ClassLoader / defineClass / Unsafe / sun.misc / jdk.internal）
        Pattern.compile("Class\\s*\\.\\s*forName|ClassLoader|defineClass|Unsafe|sun\\.misc|jdk\\.internal|sun\\.reflect"),
        // MethodHandles / VarHandle —— 反射的现代替代 API，可绕过传统反射黑名单
        Pattern.compile("MethodHandles|VarHandle|java\\.lang\\.invoke\\.[A-Za-z_]\\w*"),
        // 反射包 / 动态代理 —— Proxy 可在运行时生成恶意接口实现
        Pattern.compile("java\\.lang\\.reflect\\.[A-Za-z_]\\w*|Proxy\\s*\\.|Proxy\\s+\\w+|newProxyInstance"),
        // 线程和并发
        Pattern.compile("Thread\\s+\\w+|Executors\\s*\\.|ThreadPoolExecutor|Runtime\\s*\\.\\s*addShutdownHook|ForkJoinPool"),
        // 系统属性和环境变量
        Pattern.compile("System\\s*\\.\\s*getProperty|System\\s*\\.\\s*setProperty|System\\s*\\.\\s*getenv"),
        // 安全管理器篡改
        Pattern.compile("SecurityManager|setSecurityManager|System\\s*\\.\\s*setSecurityManager"),
        // JNI / native 方法 / 本地库加载
        Pattern.compile("native\\s+\\w|System\\s*\\.\\s*loadLibrary|System\\s*\\.\\s*load\\s*\\("),
    };

    // Java 源码中的 Unicode 转义序列（反斜杠 u + 4 位十六进制）
    // 注意：源码注释中不能直接写反斜杠 u 加 4 位十六进制，否则 Java 词法器会将其当作 Unicode 转义处理。
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    @Override
    public String getToolCode() {
        return "code_java";
    }

    @Override
    public String getToolName() {
        return "Java代码执行";
    }

    @Override
    public String getDescription() {
        return "编译并执行Java代码片段，返回执行结果。在受限环境中运行。";
    }

    @Override
    public String getConfigSchema() {
        return "{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"string\",\"description\":\"要执行的Java代码，需包含Main类和main方法\"}},\"required\":[\"code\"]}";
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long startTime = System.currentTimeMillis();
        ToolExecuteResult result = new ToolExecuteResult();
        result.setToolCode(getToolCode());
        Path tempDir = null;

        try {
            Map<String, Object> params = request.getParameters();
            String code = (String) params.get("code");

            if (code == null || code.isBlank()) {
                result.setSuccess(false);
                result.setErrorMessage("代码不能为空");
                return result;
            }

            if (code.length() > 10000) {
                result.setSuccess(false);
                result.setErrorMessage("代码长度超过限制（最大10000字符）");
                return result;
            }

            if (containsDangerousCode(code)) {
                result.setSuccess(false);
                result.setErrorMessage("代码包含危险操作，已被拦截");
                return result;
            }

            tempDir = Files.createTempDirectory("java_safe_exec_");
            setStrictPermissions(tempDir);

            Path javaFile = tempDir.resolve("Main.java");
            Files.writeString(javaFile, code);
            setStrictPermissions(javaFile);

            // 创建最小权限 SecurityManager policy 文件
            Path policyFile = tempDir.resolve("java_sandbox.policy");
            Files.writeString(policyFile, buildSecurityPolicy(tempDir));
            setStrictPermissions(policyFile);

            // 编译（异步读取输出流，避免 stdout 缓冲区满导致 waitFor 死锁）
            ProcessBuilder compilePb = new ProcessBuilder("javac", "Main.java");
            compilePb.directory(tempDir.toFile());
            compilePb.redirectErrorStream(true);
            Process compileProcess = compilePb.start();

            StringBuilder compileOutput = new StringBuilder();
            Thread compileReaderThread = asyncReadStream(compileProcess, compileOutput);

            boolean compileFinished = compileProcess.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!compileFinished) {
                compileProcess.destroyForcibly();
                compileReaderThread.interrupt();
                result.setSuccess(false);
                result.setErrorMessage("编译超时");
                return result;
            }
            compileReaderThread.join(2000);

            int compileExitCode = compileProcess.exitValue();
            if (compileExitCode != 0) {
                result.setSuccess(false);
                result.setErrorMessage("编译失败:\n" + compileOutput);
                return result;
            }

            // 运行（启用 SecurityManager，使用自定义 policy 文件，"==" 双等号覆盖默认 policy）
            String policyArg = policyFile.toAbsolutePath().toString().replace('\\', '/');
            ProcessBuilder runPb = new ProcessBuilder(
                "java",
                "-Xmx64m",                              // 限制堆内存
                "-Xss256k",                             // 限制栈大小
                "-Djava.security.manager",              // 启用安全管理器
                "-Djava.security.policy==" + policyArg, // 双等号覆盖默认 policy
                "Main"
            );
            runPb.directory(tempDir.toFile());
            runPb.redirectErrorStream(true);
            Process runProcess = runPb.start();

            StringBuilder output = new StringBuilder();
            Thread runReaderThread = asyncReadStream(runProcess, output);

            boolean runFinished = runProcess.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!runFinished) {
                runProcess.destroyForcibly();
                runReaderThread.interrupt();
                result.setSuccess(false);
                result.setErrorMessage("执行超时（超过" + TIMEOUT_SECONDS + "秒）");
                return result;
            }
            runReaderThread.join(2000);

            int runExitCode = runProcess.exitValue();
            String outputStr = output.toString();
            if (outputStr.length() > MAX_OUTPUT_LENGTH) {
                outputStr = outputStr.substring(0, (int) MAX_OUTPUT_LENGTH) + "\n...[输出过长，已截断]";
            }

            if (runExitCode == 0) {
                result.setSuccess(true);
                result.setData(outputStr.trim());
            } else {
                result.setSuccess(false);
                result.setErrorMessage("执行失败（退出码: " + runExitCode + "）\n" + outputStr);
            }

        } catch (Exception e) {
            log.error("Java代码执行异常", e);
            result.setSuccess(false);
            result.setErrorMessage("执行异常: " + e.getMessage());
        } finally {
            // 确保临时目录被清理
            if (tempDir != null) {
                cleanup(tempDir);
            }
        }

        result.setExecuteTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 异步读取子进程输出流（已通过 {@code redirectErrorStream(true)} 合并 stderr 到 stdout）。
     *
     * <p>必须在 {@link Process#waitFor} 之前启动读取线程，否则当子进程 stdout 缓冲区
     * （通常 64KB）写满后，子进程会阻塞在 write 调用上，导致 {@code waitFor} 永远不返回。
     */
    private Thread asyncReadStream(Process process, StringBuilder sink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sink.append(line).append("\n");
                }
            } catch (Exception e) {
                // 进程被强杀时会发生 IOException，忽略
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 生成最小权限 Java Security policy 文件内容。
     *
     * <p>仅授予：
     * <ul>
     *   <li>读取所有系统属性（用于打印信息，无害）；</li>
     *   <li>JVM 停止线程（shutdown hook 清理需要）；</li>
     *   <li>临时目录的读 / 写 / 删除（让程序能读写自身工作目录）。</li>
     * </ul>
     * 显式不授予：网络、反射（suppressAccessChecks）、exitVM、createClassLoader、
     * loadLibrary、临时目录外的任何文件访问。
     */
    private String buildSecurityPolicy(Path tempDir) {
        // policy 文件中反斜杠是转义字符，必须使用正斜杠
        String dir = tempDir.toAbsolutePath().toString().replace('\\', '/');
        return "// Java Sandbox Security Policy (auto-generated)\n" +
            "// 最小权限：仅读系统属性 + 读写临时目录；无网络/反射/exitVM/loadLibrary\n" +
            "grant {\n" +
            "    // 读取所有系统属性\n" +
            "    permission java.security.PropertyPermission \"*\", \"read\";\n" +
            "\n" +
            "    // JVM 关闭时停止 shutdown hook 线程所需\n" +
            "    permission java.lang.RuntimePermission \"stopThread\";\n" +
            "\n" +
            "    // 临时目录及其下所有文件的读 / 写 / 删除\n" +
            "    permission java.io.FilePermission \"" + dir + "/-\", \"read,write,delete\";\n" +
            "    permission java.io.FilePermission \"" + dir + "\", \"read\";\n" +
            "\n" +
            "    // 显式不授予：\n" +
            "    // - java.lang.RuntimePermission \"exitVM\"         (防止恶意退出宿主 JVM)\n" +
            "    // - java.lang.RuntimePermission \"createClassLoader\"\n" +
            "    // - java.lang.RuntimePermission \"getClassLoader\"\n" +
            "    // - java.lang.RuntimePermission \"suppressAccessChecks\" (反射)\n" +
            "    // - java.lang.RuntimePermission \"loadLibrary.*\"  (防止 JNI 加载本地库)\n" +
            "    // - java.net.SocketPermission                   (无任何网络访问)\n" +
            "    // - 其他 java.io.FilePermission                  (无临时目录外的文件访问)\n" +
            "};\n";
    }

    /**
     * 为临时文件 / 目录设置 600 权限（仅所有者可读写，目录加执行）。
     * 在不支持 POSIX 权限的文件系统（如 Windows NTFS）上静默跳过。
     */
    private void setStrictPermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            if (Files.isDirectory(path)) {
                perms.add(PosixFilePermission.OWNER_EXECUTE);
            }
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            // Windows 等非 POSIX 文件系统不支持，跳过
        } catch (Exception e) {
            log.warn("设置文件权限失败: {}", path, e);
        }
    }

    private boolean containsDangerousCode(String code) {
        // 第一遍：规范化后直接匹配
        String normalized = normalizeCode(code);
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                log.warn("检测到危险Java代码模式: {}", pattern.pattern());
                return true;
            }
        }
        // 第二遍：先做"反斜杠 u + 4 位十六进制"解码（模拟 javac 阶段的 Unicode 解码），再规范化匹配
        // 防止如 反斜杠u0052 解码后变为 R（拼成 Runtime）、反斜杠u0066 解码后变为 f（拼成 Class.forName）等绕过手段
        String decoded = normalizeCode(decodeUnicodeEscapes(code));
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(decoded).find()) {
                log.warn("检测到危险Java代码模式（Unicode解码后）: {}", pattern.pattern());
                return true;
            }
        }
        return false;
    }

    /**
     * 解码 Java 源码中的 Unicode 转义序列（反斜杠 u + 4 位十六进制）。
     * 注意：javac 在词法分析前会先做 Unicode 解码，因此源码中的"反斜杠u0052untime"
     * 在编译阶段等价于 "Runtime"，必须在检测阶段模拟此行为。
     */
    private String decodeUnicodeEscapes(String code) {
        Matcher matcher = UNICODE_ESCAPE.matcher(code);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            char c = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(c)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 规范化代码用于检测：去除注释、字符串字面量，统一空白。
     */
    private String normalizeCode(String code) {
        String normalized = code;
        // 去除多行注释
        normalized = normalized.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        // 去除单行注释
        normalized = normalized.replaceAll("//.*", "");
        // 去除字符串字面量
        normalized = normalized.replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"");
        normalized = normalized.replaceAll("'(?:[^'\\\\]|\\\\.)*'", "''");
        // 统一空白
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.toLowerCase();
    }

    private void cleanup(Path dir) {
        try {
            Files.walk(dir).sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    log.warn("清理临时文件失败: {}", p);
                }
            });
        } catch (Exception e) {
            log.warn("清理临时目录失败", e);
        }
    }
}
