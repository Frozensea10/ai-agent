package com.agent.mcp.tool;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

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
            "insert", "update", "delete", "drop", "create", "alter", "truncate", "grant", "revoke"
    );

    @Autowired
    private DataSource dataSource;

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

        try {
            Map<String, Object> params = request.getParameters();
            String sql = ((String) params.get(PARAM_SQL)).trim();

            if (sql == null || sql.isBlank()) {
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

                ResultSet rs = pstmt.executeQuery();
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

        } catch (SQLException e) {
            log.error("数据库查询失败", e);
            result.setSuccess(false);
            result.setErrorMessage("查询失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("数据库查询异常", e);
            result.setSuccess(false);
            result.setErrorMessage("执行异常: " + e.getMessage());
        }

        return finishResult(result, startTime);
    }

    private ToolExecuteResult finishResult(ToolExecuteResult result, long startTime) {
        result.setExecuteTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private boolean isSelectOnly(String sql) {
        String normalized = sql.toLowerCase().replaceAll("\\s+", " ").trim();
        if (!normalized.startsWith(SELECT_PREFIX.toLowerCase())) {
            return false;
        }
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (normalized.contains(" " + keyword + " ") || normalized.contains("(" + keyword + " ")) {
                return false;
            }
        }
        return true;
    }
}
