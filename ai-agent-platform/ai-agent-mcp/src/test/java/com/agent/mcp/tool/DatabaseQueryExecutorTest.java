package com.agent.mcp.tool;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseQueryExecutorTest {

    @Mock
    private DataSource dataSource;

    @InjectMocks
    private DatabaseQueryExecutor executor;

    private ToolExecuteRequest createRequest(String sql) {
        ToolExecuteRequest request = new ToolExecuteRequest();
        Map<String, Object> params = new HashMap<>();
        params.put("sql", sql);
        request.setParameters(params);
        return request;
    }

    @Test
    @DisplayName("SQL 为空返回错误")
    void shouldReturnErrorWhenSqlEmpty() {
        ToolExecuteResult result = executor.execute(createRequest(""));

        assertFalse(result.isSuccess());
        assertEquals("SQL语句不能为空", result.getErrorMessage());
        assertNotNull(result.getExecuteTimeMs());
    }

    @Test
    @DisplayName("非 SELECT 语句返回错误")
    void shouldReturnErrorWhenNotSelect() {
        ToolExecuteResult result = executor.execute(createRequest("UPDATE users SET name = 'x'"));

        assertFalse(result.isSuccess());
        assertEquals("仅支持SELECT查询语句", result.getErrorMessage());
    }

    @Test
    @DisplayName("SQL 包含危险关键字返回错误")
    void shouldReturnErrorWhenContainsForbiddenKeyword() {
        ToolExecuteResult result = executor.execute(createRequest("SELECT 1; DROP TABLE users"));

        assertFalse(result.isSuccess());
        assertEquals("仅支持SELECT查询语句", result.getErrorMessage());
    }

    @Test
    @DisplayName("超长 SQL 返回错误")
    void shouldReturnErrorWhenSqlTooLong() {
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < 2000; i++) {
            sql.append("a");
        }

        ToolExecuteResult result = executor.execute(createRequest(sql.toString()));

        assertFalse(result.isSuccess());
        assertEquals("SQL语句长度超过限制（最大2000字符）", result.getErrorMessage());
    }

    @Test
    @DisplayName("成功查询返回结果")
    void shouldExecuteQuerySuccessfully() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement pstmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);

        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement("SELECT id, name FROM users")).thenReturn(pstmt);
        when(pstmt.executeQuery()).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        when(metaData.getColumnLabel(2)).thenReturn("name");
        when(rs.next()).thenReturn(true, false, false);
        when(rs.getObject(1)).thenReturn(1);
        when(rs.getObject(2)).thenReturn("Alice");

        ToolExecuteResult result = executor.execute(createRequest("SELECT id, name FROM users"));

        assertTrue(result.isSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<String> columns = (List<String>) data.get("columns");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
        assertEquals(2, columns.size());
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).get("id"));
        assertEquals("Alice", rows.get(0).get("name"));
        assertEquals(1, data.get("totalRows"));
        assertFalse((Boolean) data.get("hasMore"));
    }

    @Test
    @DisplayName("结果超过最大返回行数时 hasMore 为 true")
    void shouldLimitReturnRowsAndSetHasMore() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement pstmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);

        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement("SELECT id FROM users")).thenReturn(pstmt);
        when(pstmt.executeQuery()).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        // 150 行数据，返回 100 行后仍剩余 50 行
        when(rs.next()).thenReturn(true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, false);
        when(rs.getObject(1)).thenReturn(1);

        ToolExecuteResult result = executor.execute(createRequest("SELECT id FROM users"));

        assertTrue(result.isSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
        assertEquals(100, rows.size());
        assertEquals(100, data.get("totalRows"));
        assertTrue((Boolean) data.get("hasMore"));
    }

    @Test
    @DisplayName("SQL 执行异常返回错误")
    void shouldReturnErrorWhenSqlExecutionFails() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement pstmt = mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement("SELECT * FROM users")).thenReturn(pstmt);
        when(pstmt.executeQuery()).thenThrow(new SQLException("syntax error"));

        ToolExecuteResult result = executor.execute(createRequest("SELECT * FROM users"));

        assertFalse(result.isSuccess());
        assertEquals("查询失败: syntax error", result.getErrorMessage());
    }
}
