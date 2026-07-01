package com.agent.mcp.tool;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
public class JavaCodeExecutor implements BuiltInToolExecutor {

    private static final long TIMEOUT_SECONDS = 30;
    private static final long MAX_OUTPUT_LENGTH = 10000;

    // 更严格的危险代码检测模式
    private static final Pattern[] DANGEROUS_PATTERNS = {
        // 进程执行
        Pattern.compile("Runtime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*exec|ProcessBuilder|Process\\s+"),
        // 系统退出
        Pattern.compile("System\\s*\\.\\s*exit|Runtime\\s*\\.\\s*halt"),
        // 文件操作
        Pattern.compile("java\\.io\\.File|java\\.nio\\.file\\.Files|java\\.nio\\.file\\.Paths|FileInputStream|FileOutputStream|RandomAccessFile"),
        // 网络操作
        Pattern.compile("java\\.net\\.Socket|java\\.net\\.URL|java\\.net\\.HttpURLConnection|java\\.net\\.InetAddress"),
        // 反射和类加载
        Pattern.compile("Class\\s*\\.\\s*forName|ClassLoader|defineClass|Unsafe|sun\\.misc"),
        // 线程和并发
        Pattern.compile("Thread\\s+\\w+|Executors\\s*\\.|ThreadPoolExecutor|Runtime\\s*\\.\\s*addShutdownHook"),
        // 系统属性
        Pattern.compile("System\\s*\\.\\s*getProperty|System\\s*\\.\\s*setProperty|System\\s*\\.\\s*getenv"),
        // 安全管理器
        Pattern.compile("SecurityManager|setSecurityManager"),
    };

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
            Path javaFile = tempDir.resolve("Main.java");
            Files.writeString(javaFile, code);

            // 编译
            ProcessBuilder compilePb = new ProcessBuilder("javac", "Main.java");
            compilePb.directory(tempDir.toFile());
            compilePb.redirectErrorStream(true);
            Process compileProcess = compilePb.start();

            boolean compileFinished = compileProcess.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!compileFinished) {
                compileProcess.destroyForcibly();
                result.setSuccess(false);
                result.setErrorMessage("编译超时");
                return result;
            }

            BufferedReader compileReader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()));
            StringBuilder compileOutput = new StringBuilder();
            String line;
            while ((line = compileReader.readLine()) != null) {
                compileOutput.append(line).append("\n");
            }

            int compileExitCode = compileProcess.exitValue();
            if (compileExitCode != 0) {
                result.setSuccess(false);
                result.setErrorMessage("编译失败:\n" + compileOutput);
                return result;
            }

            // 运行（使用受限参数）
            ProcessBuilder runPb = new ProcessBuilder(
                "java",
                "-Xmx64m",           // 限制堆内存
                "-Xss256k",          // 限制栈大小
                "-Djava.security.manager",  // 启用安全管理器
                "Main"
            );
            runPb.directory(tempDir.toFile());
            runPb.redirectErrorStream(true);
            Process runProcess = runPb.start();

            boolean runFinished = runProcess.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!runFinished) {
                runProcess.destroyForcibly();
                result.setSuccess(false);
                result.setErrorMessage("执行超时（超过" + TIMEOUT_SECONDS + "秒）");
                return result;
            }

            BufferedReader runReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
            StringBuilder output = new StringBuilder();
            while ((line = runReader.readLine()) != null) {
                output.append(line).append("\n");
                if (output.length() > MAX_OUTPUT_LENGTH) {
                    output.append("\n...[输出过长，已截断]");
                    break;
                }
            }

            int runExitCode = runProcess.exitValue();

            if (runExitCode == 0) {
                result.setSuccess(true);
                result.setData(output.toString().trim());
            } else {
                result.setSuccess(false);
                result.setErrorMessage("执行失败（退出码: " + runExitCode + "）\n" + output);
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

    private boolean containsDangerousCode(String code) {
        String normalized = normalizeCode(code);
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                log.warn("检测到危险Java代码模式: {}", pattern.pattern());
                return true;
            }
        }
        return false;
    }

    /**
     * 规范化代码用于检测：去除注释、字符串，统一空白
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
