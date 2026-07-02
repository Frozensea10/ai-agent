package com.agent.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI aiAgentPlatformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Agent Platform API")
                        .version("1.0.0")
                        .description("企业级 AI Agent 智能助手平台统一 OpenAPI 文档"));
    }
}
