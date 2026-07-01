package com.agent.mcp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("built_in_tool")
public class BuiltInTool {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolName;

    private String toolCode;

    private String description;

    private String toolType;

    private String configSchema;

    private String status;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
