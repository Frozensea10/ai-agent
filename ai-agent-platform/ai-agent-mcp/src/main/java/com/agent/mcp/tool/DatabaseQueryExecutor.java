package com.agent.mcp.tool;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DatabaseQueryExecutor implements BuiltInToolExecutor {

    private static final int MAX_SQL_LENGTH = 2000;
    private static final int MAX_QUERY_ROWS = 1000;
    private static final int QUERY_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETURN_ROWS = 100;
    private static final String PARAM_SQL = "sql";
    private static final String SELECT_PREFIX = "SELECT";
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "insert", "update", "delete", "drop", "create", "alter", "truncate",
            "grant", "revoke", "merge", "replace", "call", "handler", "lock", "unlock"
    );
    private static final Set<String> FORBIDDEN_TARGETS = Set.of(
            "into outfile", "into dumpfile", "load_file", "information_schema", "mysql.user"
    );
    private static final Pattern BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT_PATTERN = Pattern.compile("--[^\\n]*");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\s\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]+");

    private final DataSource dataSource;

    public DatabaseQueryExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getToolCode() {
        return "db_query";
    }

    @Override
    public String getToolName() {
        return "数据库查询";
    }

    @Override
    public String getDescription() {
        return "执行只读SQL查询，返回查询结果。仅支持SELECT语句。";
    }

    @Override
    public String getConfigSchema() {
        return "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\",\"description\":\"SQL查询语句，仅支持SELECT\"}},\"required\":[\"sql\"]}";
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long startTime = System.currentTimeMillis();
        ToolExecuteResult result = new ToolExecuteResult();
        result.setToolCode(getToolCode());

        String sql = null;
        try {
            Map<String, Object> params = request.getParameters();
            Object sqlObj = params == null ? null : params.get(PARAM_SQL);
            if (sqlObj == null) {
                result.setSuccess(false);
                result.setErrorMessage("SQL语句不能为空");
                return finishResult(result, startTime);
            }
            sql = sqlObj.toString().trim();

            if (sql.isBlank()) {
                result.setSuccess(false);
                result.setErrorMessage("SQL语句不能为空");
                return finishResult(result, startTime);
            }

            if (!isSelectOnly(sql)) {
                result.setSuccess(false);
                result.setErrorMessage("仅支持SELECT查询语句");
                return finishResult(result, startTime);
            }

            if (sql.length() > MAX_SQL_LENGTH) {
                result.setSuccess(false);
                result.setErrorMessage("SQL语句长度超过限制（最大" + MAX_SQL_LENGTH + "字符）");
                return finishResult(result, startTime);
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setMaxRows(MAX_QUERY_ROWS);
                pstmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

                try (ResultSet rs = pstmt.executeQuery()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    List<String> columns = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(metaData.getColumnLabel(i));
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    int rowCount = 0;
                    while (rs.next() && rowCount < MAX_RETURN_ROWS) {
                        Map<String, Object> row = new LinkedHashMap<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(columns.get(i - 1), rs.getObject(i));
                        }
                        rows.add(row);
                        rowCount++;
                    }

                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("columns", columns);
                    data.put("rows", rows);
                    data.put("totalRows", rowCount);
                    data.put("hasMore", rs.next());

                    result.setSuccess(true);
                    result.setData(data);
                }
            }

        } catch (SQLException e) {
            log.error("数据库查询失败, SQL: {}", sql, e);
            result.setSuccess(false);
            result.setErrorMessage("查询执行失败，请联系管理员");
        } catch (Exception e) {
            log.error("数据库查询异常, SQL: {}", sql, e);
            result.setSuccess(false);
            result.setErrorMessage("查询执行异常，请联系管理员");
        }

        return finishResult(result, startTime);
    }

    private ToolExecuteResult finishResult(ToolExecuteResult result, long startTime) {
        result.setExecuteTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private boolean isSelectOnly(String sql) {
        // 1. 去除块注释 /* */ （包括跨行）
        String noBlock = BLOCK_COMMENT_PATTERN.matcher(sql).replaceAll(" ");
        // 2. 去除行注释 --
        String noLine = LINE_COMMENT_PATTERN.matcher(noBlock).replaceAll(" ");
        // 3. 规范化空白（包括 NBSP 等非标准空白）并转小写
        String normalized = WHITESPACE_PATTERN.matcher(noLine).replaceAll(" ").trim().toLowerCase(Locale.ROOT);

        if (normalized.isEmpty() || !normalized.startsWith(SELECT_PREFIX.toLowerCase(Locale.ROOT))) {
            return false;
        }
        // 校验首单词完整为 select，防止 "selectxxx" 形式绕过
        if (normalized.length() > SELECT_PREFIX.length()
                && !Character.isWhitespace(normalized.charAt(SELECT_PREFIX.length()))) {
            return false;
        }

        // 4. 检查危险目标
        for (String target : FORBIDDEN_TARGETS) {
            if (normalized.contains(target)) {
                return false;
            }
        }

        // 5. 检查危险关键字（使用单词边界，防止 "block" 误命中 "lock" 等）
        for (String keyword : FORBIDDEN_KEYWORDS) {
            String boundary = "(?<![a-z0-9_])" + keyword + "(?![a-z0-9_])";
            if (Pattern.compile(boundary).matcher(normalized).find()) {
                return false;
            }
        }
        return true;
    }
}
