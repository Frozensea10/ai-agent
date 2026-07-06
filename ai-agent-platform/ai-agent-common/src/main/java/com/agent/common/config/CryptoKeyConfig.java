package com.agent.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 加密密钥配置
 *
 * <p>CryptoUtil 是静态工具类，从环境变量 {@code APP_ENCRYPTION_KEY} 读取密钥。
 * 开发环境通过 application-dev.yml 的 {@code app.encryption.key} 配置，
 * 本类在启动时将其设置到 System property，供 CryptoUtil 读取。
 *
 * <p>放在 common 模块，所有依赖 common 的服务（core/chat 等）都能自动注入。
 *
 * <p>生产环境仍应通过环境变量 {@code APP_ENCRYPTION_KEY} 提供（base64 编码的 32/16 字节密钥）。
 */
@Slf4j
@Configuration
public class CryptoKeyConfig {

    @Value("${app.encryption.key:}")
    private String encryptionKey;

    @PostConstruct
    public void init() {
        if (encryptionKey != null && !encryptionKey.isBlank()) {
            if (System.getProperty("APP_ENCRYPTION_KEY") == null
                    && System.getenv("APP_ENCRYPTION_KEY") == null) {
                System.setProperty("APP_ENCRYPTION_KEY", encryptionKey);
                log.info("已从 application.yml 注入 APP_ENCRYPTION_KEY (开发模式)");
            }
        }
    }
}
