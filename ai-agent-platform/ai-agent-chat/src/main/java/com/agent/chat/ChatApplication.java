package com.agent.chat;

import com.agent.common.config.MybatisPlusConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.agent.chat", "com.agent.core.llm", "com.agent.common"})
@Import(MybatisPlusConfig.class)
@MapperScan(basePackages = "com.agent.chat.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class ChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
