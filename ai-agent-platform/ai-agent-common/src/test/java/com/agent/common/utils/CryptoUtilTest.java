package com.agent.common.utils;

import com.agent.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoUtilTest {

    /**
     * 注入一个固定的 AES-256 密钥到 CryptoUtil.key 静态字段，
     * 跳过对环境变量 APP_ENCRYPTION_KEY 的依赖，使测试可独立运行。
     */
    @BeforeAll
    static void injectKey() throws Exception {
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = (byte) i;
        }
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        Field field = CryptoUtil.class.getDeclaredField("key");
        field.setAccessible(true);
        field.set(null, secretKey);
    }

    @Test
    @DisplayName("加密后解密应还原明文")
    void encrypt_thenDecrypt_shouldReturnOriginalText() {
        String plain = "sk-api-key-1234567890";

        String cipher = CryptoUtil.encrypt(plain);

        assertThat(cipher).isNotEqualTo(plain);
        assertThat(CryptoUtil.isEncrypted(cipher)).isTrue();
        assertThat(CryptoUtil.decrypt(cipher)).isEqualTo(plain);
    }

    @Test
    @DisplayName("加密密文以 ENC: 前缀开头且每次 IV 不同")
    void encrypt_shouldProduceDifferentCipherEachTime() {
        String plain = "same-secret";

        String c1 = CryptoUtil.encrypt(plain);
        String c2 = CryptoUtil.encrypt(plain);

        assertThat(c1).startsWith("ENC:");
        assertThat(c2).startsWith("ENC:");
        // GCM 模式每次产生随机 IV，相同明文应得到不同密文
        assertThat(c1).isNotEqualTo(c2);
        assertThat(CryptoUtil.decrypt(c1)).isEqualTo(plain);
        assertThat(CryptoUtil.decrypt(c2)).isEqualTo(plain);
    }

    @Test
    @DisplayName("已是 ENC: 前缀的值不重复加密（幂等）")
    void encrypt_shouldNotEncryptAgainIfAlreadyEncrypted() {
        String plain = "my-plain";
        String cipher = CryptoUtil.encrypt(plain);

        String again = CryptoUtil.encrypt(cipher);

        assertThat(again).isEqualTo(cipher);
    }

    @Test
    @DisplayName("解密非 ENC: 前缀的值原样返回（向后兼容明文）")
    void decrypt_shouldReturnAsIsForPlaintext() {
        assertThat(CryptoUtil.decrypt("plain-text")).isEqualTo("plain-text");
    }

    @Test
    @DisplayName("encrypt(null) 返回 null")
    void encrypt_null_shouldReturnNull() {
        assertThat(CryptoUtil.encrypt(null)).isNull();
    }

    @Test
    @DisplayName("decrypt(null) 返回 null")
    void decrypt_null_shouldReturnNull() {
        assertThat(CryptoUtil.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("isEncrypted 对 null / 明文 / 密文的行为")
    void isEncrypted_shouldWorkAsExpected() {
        assertThat(CryptoUtil.isEncrypted(null)).isFalse();
        assertThat(CryptoUtil.isEncrypted("plain")).isFalse();
        assertThat(CryptoUtil.isEncrypted("ENC:something")).isTrue();
    }

    @Test
    @DisplayName("密文格式错误（缺少分隔符）抛 BusinessException")
    void decrypt_malformedCipher_shouldThrowBusinessException() {
        assertThatThrownBy(() -> CryptoUtil.decrypt("ENC:no-separator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密文格式错误");
    }

    @Test
    @DisplayName("使用错误密钥解密应抛 BusinessException（解密失败）")
    void decrypt_withWrongKey_shouldThrowBusinessException() throws Exception {
        String plain = "secret-data";
        String cipher = CryptoUtil.encrypt(plain);

        // 替换为另一个不同的密钥
        byte[] wrongKeyBytes = new byte[32];
        for (int i = 0; i < wrongKeyBytes.length; i++) {
            wrongKeyBytes[i] = (byte) (i + 1);
        }
        SecretKey wrongKey = new SecretKeySpec(wrongKeyBytes, "AES");
        Field field = CryptoUtil.class.getDeclaredField("key");
        field.setAccessible(true);
        field.set(null, wrongKey);

        assertThatThrownBy(() -> CryptoUtil.decrypt(cipher))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("解密失败");

        // 还原密钥，避免污染后续测试
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = (byte) i;
        }
        field.set(null, new SecretKeySpec(keyBytes, "AES"));
    }

    @Test
    @DisplayName("密钥未配置时 encrypt 抛 BusinessException")
    void encrypt_whenKeyNotConfigured_shouldThrow() throws Exception {
        String plain = "x";
        // 暂存原密钥
        Field field = CryptoUtil.class.getDeclaredField("key");
        field.setAccessible(true);
        SecretKey original = (SecretKey) field.get(null);
        // 清空使 getKey() 重新走环境变量分支
        field.set(null, null);

        try {
            assertThatThrownBy(() -> CryptoUtil.encrypt(plain))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("加密密钥未配置");
        } finally {
            // 还原，避免污染其他测试
            field.set(null, original);
        }
    }
}
