package com.agent.mcp.tool;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Python 代码执行器（受限环境）。
 *
 * <p>安全提示（重要）：
 * <ul>
 *   <li>本执行器仅提供基于静态规则匹配和危险内置函数移除的浅层防护，<b>无法替代真正的操作系统级沙箱</b>。
 *       Python 语言层面的限制可被反射、C 扩展、字节码操作等方式绕过。</li>
 *   <li>建议在 Docker 容器内运行本服务，配合 seccomp / namespace 隔离，使用 <code>nobody</code> 等低权限用户运行子进程。</li>
 *   <li>对 CPU / 内存 / 磁盘 / 网络等资源应通过 cgroups 进行限制。</li>
 *   <li>生产环境应考虑使用 firejail、nsjail、gVisor 等专门的沙箱工具。</li>
 *   <li>临时文件目录应挂载为 tmpfs 并设置 noexec，避免持久化攻击。</li>
 * </ul>
 */
@Slf4j
@Component
public class PythonCodeExecutor implements BuiltInToolExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${mcp.python.executable:python3}")
    private String pythonExecutable;

    private static final long TIMEOUT_SECONDS = 30;
    private static final long MAX_OUTPUT_LENGTH = 10000;

    // 危险代码检测模式（注意：静态匹配无法 100% 防御绕过，必须配合 OS 级隔离）
    private static final Pattern[] DANGEROUS_PATTERNS = {
        // 系统命令执行
        Pattern.compile("os\\s*\\.\\s*system|subprocess\\s*\\.\\s*(call|run|Popen|check_output|check_call)|commands\\s*\\.\\s*"),
        // 动态导入：__import__ / importlib / imp
        Pattern.compile("__import__|importlib|\\bimp\\s*\\."),
        // 动态执行与编译：eval / exec / compile
        Pattern.compile("eval\\s*\\(|exec\\s*\\(|compile\\s*\\("),
        // 文件操作：绝对路径、盘符、相对路径穿越（../）
        Pattern.compile("open\\s*\\(\\s*['\"]\\s*[/\\\\]|open\\s*\\(\\s*['\"]\\s*[A-Za-z]:|open\\s*\\(\\s*['\"]\\s*\\.\\."),
        // 网络相关
        Pattern.compile("import\\s+(socket|urllib|requests|http\\.client|ftplib|telnetlib|paramiko|websocket)"),
        // 文件系统操作
        Pattern.compile("shutil\\s*\\.\\s*(rmtree|move|copy)|os\\s*\\.\\s*(remove|rmdir|unlink|rename|chmod|mkdir|chown)"),
        // 底层内存 / 模块操作
        Pattern.compile("ctypes|mmap|sys\\s*\\.\\s*modules"),
        // 反射绕过：双下划线属性访问
        Pattern.compile("getattr\\s*\\(|__getattribute__|__class__|__bases__|__base__|__subclasses__|__mro__|__globals__|__builtins__|__code__|__func__"),
        // 内置函数访问：globals / locals / vars / dir
        Pattern.compile("globals\\s*\\(|locals\\s*\\(|vars\\s*\\(|\\bdir\\s*\\("),
        // 编码绕过：base64 / codecs
        Pattern.compile("base64|codecs|decode\\s*\\(|encode\\s*\\("),
        // 进程 / 信号操作
        Pattern.compile("os\\s*\\.\\s*(fork|kill|getpid|spawn|exec)|signal\\s*\\.\\s*"),
        // 环境与路径探测
        Pattern.compile("os\\s*\\.\\s*(environ|getcwd|chdir|listdir|walk|stat)"),
    };

    @Override
    public String getToolCode() {
        return "code_python";
    }

    @Override
    public String getToolName() {
        return "Python代码执行";
    }

    @Override
    public String getDescription() {
        return "在受限环境中执行Python代码片段，返回执行结果。支持标准输出捕获。";
    }

    @Override
    public String getConfigSchema() {
        return "{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"string\",\"description\":\"要执行的Python代码\"}},\"required\":[\"code\"]}";
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long startTime = System.currentTimeMillis();
        ToolExecuteResult result = new ToolExecuteResult();
        result.setToolCode(getToolCode());
        Path tempFile = null;

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

            // 添加安全限制头
            String safeCode = addSafetyRestrictions(code);

            tempFile = Files.createTempFile("python_safe_", ".py");
            Files.writeString(tempFile, safeCode);

            // -S: 禁用 site-packages（避免加载第三方危险模块）
            // -E: 忽略 PYTHON* 环境变量（避免 PYTHONSTARTUP / PYTHONPATH 注入）
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-S", "-E", tempFile.toString());
            // 合并 stderr 到 stdout，便于统一捕获；不调用 inheritIO()，避免子进程输出污染 JVM 日志
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 异步读取 stdout，避免子进程输出填满管道 buffer 导致 waitFor 死锁
            StringBuilder output = new StringBuilder();
            Thread readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        if (output.length() > MAX_OUTPUT_LENGTH) {
                            output.append("\n...[输出过长，已截断]");
                            break;
                        }
                    }
                } catch (Exception e) {
                    output.append("\n读取输出异常: ").append(e.getMessage());
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                // 等待读取线程结束，回收资源
                try {
                    readerThread.join(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                result.setSuccess(false);
                result.setErrorMessage("代码执行超时（超过" + TIMEOUT_SECONDS + "秒），进程已强杀");
                return result;
            }

            // 等待读取线程完成，确保捕获完整输出
            try {
                readerThread.join(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            int exitCode = process.exitValue();

            if (exitCode == 0) {
                result.setSuccess(true);
                result.setData(output.toString().trim());
            } else {
                result.setSuccess(false);
                result.setErrorMessage("执行失败（退出码: " + exitCode + "）\n" + output);
            }

        } catch (Exception e) {
            log.error("Python代码执行异常", e);
            result.setSuccess(false);
            result.setErrorMessage("执行异常: " + e.getMessage());
        } finally {
            // 确保临时文件被清理
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.warn("清理临时文件失败: {}", tempFile, e);
                }
            }
        }

        result.setExecuteTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private boolean containsDangerousCode(String code) {
        // 第一次：对原始代码规范化后检测
        String normalized = normalizeCode(code);
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                log.warn("检测到危险代码模式: {}", pattern.pattern());
                return true;
            }
        }
        // 第二次：对 Unicode / 十六进制转义解码后再检测一次，防止 \u0065val 等绕过
        String decoded = decodeEscapes(code);
        if (!decoded.equals(code)) {
            String decodedNormalized = normalizeCode(decoded);
            for (Pattern pattern : DANGEROUS_PATTERNS) {
                if (pattern.matcher(decodedNormalized).find()) {
                    log.warn("检测到危险代码模式（转义解码后）: {}", pattern.pattern());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 解码 Python 源码中常见的转义序列（反斜杠+u+四位十六进制、反斜杠+x+两位十六进制、反斜杠+U+八位十六进制），
     * 用于对抗将危险函数名通过转义隐藏的绕过手法（例如把 import、eval 中的字符转义）。
     * 注意：此方法仅做启发式解码，不保证与 Python 词法完全一致。
     * 采用字符级解析而非正则，避免 Java 源码中字面量反斜杠+u 被词法器当作 Unicode 转义。
     */
    private String decodeEscapes(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        StringBuilder result = new StringBuilder(code.length());
        int i = 0;
        int len = code.length();
        while (i < len) {
            char c = code.charAt(i);
            if (c == '\\' && i + 1 < len) {
                char next = code.charAt(i + 1);
                if (next == 'u' && i + 6 <= len && isHex(code, i + 2, 4)) {
                    int cp = Integer.parseInt(code.substring(i + 2, i + 6), 16);
                    result.appendCodePoint(cp);
                    i += 6;
                    continue;
                } else if (next == 'U' && i + 10 <= len && isHex(code, i + 2, 8)) {
                    long cp = Long.parseLong(code.substring(i + 2, i + 10), 16);
                    if (cp <= 0x10FFFFL) {
                        result.appendCodePoint((int) cp);
                        i += 10;
                        continue;
                    }
                } else if (next == 'x' && i + 4 <= len && isHex(code, i + 2, 2)) {
                    int cp = Integer.parseInt(code.substring(i + 2, i + 4), 16);
                    result.appendCodePoint(cp);
                    i += 4;
                    continue;
                }
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }

    /** 判断 code 从 start 开始的 length 个字符是否均为十六进制数字。 */
    private static boolean isHex(String code, int start, int length) {
        if (start + length > code.length()) {
            return false;
        }
        for (int j = start; j < start + length; j++) {
            char ch = code.charAt(j);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 规范化代码用于检测：去除注释、字符串，统一空白
     */
    private String normalizeCode(String code) {
        String normalized = code;
        // 去除多行字符串
        normalized = normalized.replaceAll("\"\"\"[\\s\\S]*?\"\"\"", "\"\"\"\"\"\"");
        normalized = normalized.replaceAll("'''[\\s\\S]*?'''", "''''''");
        // 去除单行注释
        normalized = normalized.replaceAll("#.*", "");
        // 去除字符串字面量（保留引号）
        normalized = normalized.replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"");
        normalized = normalized.replaceAll("'(?:[^'\\\\]|\\\\.)*'", "''");
        // 统一空白
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.toLowerCase();
    }

    /**
     * 在用户代码前注入安全限制。
     *
     * <p>注意：Python 语言层面的限制可被反射 / C 扩展绕过，此处仅作纵深防御的一环，
     * 真正的隔离必须依赖 OS 级沙箱（见类注释）。
     */
    private String addSafetyRestrictions(String code) {
        StringBuilder safeCode = new StringBuilder();
        safeCode.append("import sys\n");
        // 限制递归深度，降低栈溢出 DoS 风险
        safeCode.append("sys.setrecursionlimit(100)\n");
        // 移除危险的内置函数，使用户代码无法直接调用 __import__ / eval / exec / open 等
        // （静态检测已先行拦截，此处为运行时纵深防御）
        safeCode.append("_DANGEROUS_BUILTINS = (\n");
        safeCode.append("    '__import__', 'exec', 'eval', 'compile', 'open',\n");
        safeCode.append("    'globals', 'locals', 'vars', 'dir', 'getattr',\n");
        safeCode.append("    'setattr', 'delattr', 'input', 'breakpoint', 'memoryview',\n");
        safeCode.append(")\n");
        safeCode.append("try:\n");
        safeCode.append("    _bd = getattr(__builtins__, '__dict__', __builtins__)\n");
        safeCode.append("    for _n in _DANGEROUS_BUILTINS:\n");
        safeCode.append("        _bd.pop(_n, None)\n");
        safeCode.append("    del _bd, _n\n");
        safeCode.append("except Exception:\n");
        safeCode.append("    pass\n");
        safeCode.append("del _DANGEROUS_BUILTINS\n");
        safeCode.append("\n");
        safeCode.append(code);
        return safeCode.toString();
    }
}
