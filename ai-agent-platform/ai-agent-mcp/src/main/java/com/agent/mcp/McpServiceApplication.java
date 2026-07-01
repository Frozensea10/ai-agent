package com.agent.mcp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.agent.mcp", "com.agent.common"})
@EnableFeignClients
@Import(com.agent.common.config.MybatisPlusConfig.class)
@MapperScan(basePackages = "com.agent.mcp.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class McpServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServiceApplication.class, args);
    }
}
