package com.agent.core;

import com.agent.common.config.MybatisPlusConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.agent.core", "com.agent.common"})
@Import(MybatisPlusConfig.class)
@MapperScan(basePackages = "com.agent.core.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class CoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}
