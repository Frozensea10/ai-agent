package com.agent.user;

import com.agent.common.config.MybatisPlusConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.agent.user", "com.agent.common"})
@Import(MybatisPlusConfig.class)
@MapperScan(basePackages = "com.agent.user.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
