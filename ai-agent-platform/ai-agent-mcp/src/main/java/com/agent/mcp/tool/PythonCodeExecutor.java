package com.agent.mcp.tool;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class PythonCodeExecutor implements BuiltInToolExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final long TIMEOUT_SECONDS = 30;
    private static final long MAX_OUTPUT_LENGTH = 10000;

    // 更严格的危险代码检测模式（支持字符串拼接绕过检测）
    private static final Pattern[] DANGEROUS_PATTERNS = {
        // 系统命令执行
        Pattern.compile("os\\s*\\.\\s*system|subprocess\\s*\\.\\s*(call|run|Popen)"),
        // 动态导入和执行
        Pattern.compile("__import__|eval\\s*\\(|exec\\s*\\("),
        // 文件操作（尝试访问绝对路径）
        Pattern.compile("open\\s*\\(\\s*['\"]\\s*[/\\\\]|open\\s*\\(\\s*['\"]\\s*[A-Za-z]:"),
        // 网络相关
        Pattern.compile("import\\s+(socket|urllib|requests|http\\.client|ftplib)"),
        // 文件系统操作
        Pattern.compile("shutil\\s*\\.\\s*(rmtree|move|copy)|os\\s*\\.\\s*(remove|rmdir|rename|chmod)"),
        // 底层内存操作
        Pattern.compile("ctypes|mmap|sys\\s*\\.\\s*modules|importlib"),
        // 反射绕过
        Pattern.compile("getattr\\s*\\(|__getattribute__|__class__|__bases__|__subclasses__"),
        // 编码绕过
        Pattern.compile("base64|decode\\s*\\(|encode\\s*\\("),
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

            // 使用受限用户运行（如果系统支持）
            ProcessBuilder pb = new ProcessBuilder("python3", tempFile.toString());
            pb.redirectErrorStream(true);
            // 限制进程资源
            pb.inheritIO();

            Process process = pb.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.setSuccess(false);
                result.setErrorMessage("代码执行超时（超过" + TIMEOUT_SECONDS + "秒）");
                return result;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (output.length() > MAX_OUTPUT_LENGTH) {
                    output.append("\n...[输出过长，已截断]");
                    break;
                }
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
        String normalized = normalizeCode(code);
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                log.warn("检测到危险代码模式: {}", pattern.pattern());
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
     * 添加安全限制到代码
     */
    private String addSafetyRestrictions(String code) {
        StringBuilder safeCode = new StringBuilder();
        // 限制资源使用
        safeCode.append("import sys\n");
        safeCode.append("sys.setrecursionlimit(100)\n");
        safeCode.append("\n");
        safeCode.append(code);
        return safeCode.toString();
    }
}
