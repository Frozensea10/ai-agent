package com.agent.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.agent.knowledge", "com.agent.common"})
@Import(com.agent.common.config.MybatisPlusConfig.class)
@MapperScan(basePackages = "com.agent.knowledge.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
@EnableFeignClients(basePackages = "com.agent.knowledge.feign")
public class KnowledgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }
}
