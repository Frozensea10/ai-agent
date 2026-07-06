package com.agent.common.utils;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 对称加密工具类，基于 AES/GCM/NoPadding。
 *
 * <p>密钥从环境变量 {@code APP_ENCRYPTION_KEY} 读取，需为 base64 编码的
 * 32 字节(AES-256)或 16 字节(AES-128)密钥，无默认值。
 *
 * <p>密文格式：{@code ENC:base64(iv)||base64(ciphertext+tag)}，GCM 模式每次加密产生随机 IV。
 * 解密时若入参不以 {@code ENC:} 前缀开头，则视为明文原样返回，以兼容历史数据。
 */
@Slf4j
public class CryptoUtil {

    private static final String ENV_KEY_NAME = "APP_ENCRYPTION_KEY";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ENC_PREFIX = "ENC:";
    private static final String SEPARATOR = "||";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private static volatile SecretKey key;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtil() {
    }

    private static SecretKey getKey() {
        SecretKey existing = key;
        if (existing != null) {
            return existing;
        }
        synchronized (CryptoUtil.class) {
            if (key != null) {
                return key;
            }
            // 优先读环境变量（生产环境），其次读 System property（开发环境通过 Spring 配置注入）
            String envValue = System.getenv(ENV_KEY_NAME);
            if (envValue == null || envValue.isBlank()) {
                envValue = System.getProperty(ENV_KEY_NAME);
            }
            if (envValue == null || envValue.isBlank()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "加密密钥未配置，请设置环境变量 " + ENV_KEY_NAME
                                + "，或在 application-dev.yml 中配置 app.encryption.key");
            }
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(envValue);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        ENV_KEY_NAME + " 不是合法的 base64 编码", e);
            }
            if (keyBytes.length != 32 && keyBytes.length != 16) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        ENV_KEY_NAME + " 解码后必须为 32 字节(AES-256)或 16 字节(AES-128)，当前为 "
                                + keyBytes.length + " 字节");
            }
            key = new SecretKeySpec(keyBytes, "AES");
            return key;
        }
    }

    /**
     * 加密明文。已是 {@code ENC:} 前缀的值原样返回，避免重复加密。
     */
    public static String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        if (plainText.startsWith(ENC_PREFIX)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherTextWithTag = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return ENC_PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + SEPARATOR
                    + Base64.getEncoder().encodeToString(cipherTextWithTag);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "加密失败", e);
        }
    }

    /**
     * 解密密文。若不以 {@code ENC:} 前缀开头，视为明文原样返回（向后兼容）。
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        if (!cipherText.startsWith(ENC_PREFIX)) {
            return cipherText;
        }
        try {
            String payload = cipherText.substring(ENC_PREFIX.length());
            int sepIndex = payload.indexOf(SEPARATOR);
            if (sepIndex < 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "密文格式错误");
            }
            byte[] iv = Base64.getDecoder().decode(payload.substring(0, sepIndex));
            byte[] cipherTextWithTag = Base64.getDecoder().decode(payload.substring(sepIndex + SEPARATOR.length()));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherTextWithTag);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解密失败，请检查加密密钥是否正确", e);
        }
    }

    /**
     * 判断值是否已被加密（以 {@code ENC:} 前缀标识）。
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX);
    }
}
