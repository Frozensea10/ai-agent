package com.agent.chat;

import com.agent.common.config.MybatisPlusConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.agent.chat", "com.agent.core", "com.agent.common", "com.agent.mcp.client", "com.agent.mcp.service", "com.agent.mcp.tool", "com.agent.mcp.config"})
@Import(MybatisPlusConfig.class)
@MapperScan(basePackages = {"com.agent.chat.mapper", "com.agent.core.mapper", "com.agent.mcp.mapper"}, sqlSessionTemplateRef = "sqlSessionTemplate")
public class ChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
