package com.agent.mcp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tool_execution_log")
public class ToolExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolCode;

    private String toolType;

    private String requestParams;

    private String responseResult;

    private String status;

    private String errorMessage;

    private Long executeTimeMs;

    private LocalDateTime createdAt;
}
