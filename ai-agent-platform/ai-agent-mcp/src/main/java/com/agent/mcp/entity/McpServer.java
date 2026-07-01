package com.agent.mcp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mcp_server")
public class McpServer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String serverName;

    private String serverType;

    private String transportConfig;

    private String status;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
