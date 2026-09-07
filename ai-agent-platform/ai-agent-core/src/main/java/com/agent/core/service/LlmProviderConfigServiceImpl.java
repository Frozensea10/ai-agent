package com.agent.core.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.utils.CryptoUtil;
import com.agent.core.dto.LlmProviderConfigDTO;
import com.agent.core.entity.LlmProviderConfig;
import com.agent.core.llm.service.LLMService;
import com.agent.core.mapper.LlmProviderConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 提供商配置服务实现
 * 管理模型提供商配置的增删改查，API Key 加密存储、脱敏返回
 */
@Slf4j
@Service
public class LlmProviderConfigServiceImpl implements LlmProviderConfigService {

    /** 启用标记值。 */
    private static final int ENABLED_FLAG = 1;
    /** API Key 固定掩码。 */
    private static final String MASK = "****";
    /** 脱敏保留前缀长度。 */
    private static final int MASK_PREFIX_LEN = 3;
    /** 脱敏保留后缀长度。 */
    private static final int MASK_SUFFIX_LEN = 4;
    /** 明文长度小于等于该值时直接返回固定掩码。 */
    private static final int MASK_MIN_PLAIN_LEN = MASK_PREFIX_LEN + MASK_SUFFIX_LEN;

    private final LlmProviderConfigMapper configMapper;
    /** 延迟注入以打破 LLMService -> LlmProviderConfigService 的构造器循环依赖。 */
    private final LLMService llmService;

    /**
     * 构造函数（显式声明以便为 llmService 指定 @Lazy 打破循环依赖）。
     */
    public LlmProviderConfigServiceImpl(LlmProviderConfigMapper configMapper,
                                        @Lazy LLMService llmService) {
        this.configMapper = configMapper;
        this.llmService = llmService;
    }

    @Override
    public List<LlmProviderConfigDTO> listConfigs() {
        List<LlmProviderConfig> entities = configMapper.selectList(
                new LambdaQueryWrapper<LlmProviderConfig>().orderByAsc(LlmProviderConfig::getProviderName)
        );
        return entities.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public LlmProviderConfigDTO getConfig(String providerName) {
        LlmProviderConfig entity = configMapper.selectOne(
                new LambdaQueryWrapper<LlmProviderConfig>()
                        .eq(LlmProviderConfig::getProviderName, providerName)
        );
        return entity == null ? null : convertToDTO(entity);
    }

    @Override
    public String getApiKey(String providerName) {
        LlmProviderConfig entity = configMapper.selectOne(
                new LambdaQueryWrapper<LlmProviderConfig>()
                        .eq(LlmProviderConfig::getProviderName, providerName)
                        .eq(LlmProviderConfig::getEnabled, ENABLED_FLAG)
        );
        if (entity == null) {
            return null;
        }
        String storedKey = entity.getApiKey();
        if (storedKey == null || storedKey.isBlank()) {
            return null;
        }
        // 向后兼容：已存在的明文 API Key 在读取时自动加密保存
        if (!CryptoUtil.isEncrypted(storedKey)) {
            String encrypted = CryptoUtil.encrypt(storedKey);
            LlmProviderConfig update = new LlmProviderConfig();
            update.setId(entity.getId());
            update.setApiKey(encrypted);
            configMapper.updateById(update);
            return storedKey;
        }
        return CryptoUtil.decrypt(storedKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(LlmProviderConfigDTO dto) {
        LlmProviderConfig existing = configMapper.selectOne(
                new LambdaQueryWrapper<LlmProviderConfig>()
                        .eq(LlmProviderConfig::getProviderName, dto.getProviderName())
        );
        LlmProviderConfig entity = new LlmProviderConfig();
        entity.setProviderName(dto.getProviderName());
        // 入参若为明文则加密后存储；已是 ENC: 前缀则原样保存避免重复加密
        String apiKey = dto.getApiKey();
        if (apiKey != null && !apiKey.isBlank() && !CryptoUtil.isEncrypted(apiKey)) {
            apiKey = CryptoUtil.encrypt(apiKey);
        }
        entity.setApiKey(apiKey);
        entity.setModelName(dto.getModelName());
        entity.setEnabled(dto.getEnabled() == null ? ENABLED_FLAG : dto.getEnabled());
        if (existing != null) {
            entity.setId(existing.getId());
            configMapper.updateById(entity);
        } else {
            try {
                configMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "LLM 提供商配置已存在: " + dto.getProviderName());
            }
        }
    }

    @Override
    public void saveAndRefresh(LlmProviderConfigDTO dto) {
        saveConfig(dto);
        llmService.refreshProviderConfig(dto.getProviderName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "配置 ID 不能为空");
        }
        LlmProviderConfig entity = configMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "LLM 提供商配置不存在: " + id);
        }
        configMapper.deleteById(id);
    }

    /**
     * 将实体转换为 DTO（不返回原始 API Key，仅返回脱敏值）
     *
     * @param entity 提供商配置实体
     * @return 提供商配置 DTO
     */
    private LlmProviderConfigDTO convertToDTO(LlmProviderConfig entity) {
        LlmProviderConfigDTO dto = new LlmProviderConfigDTO();
        dto.setId(entity.getId());
        dto.setProviderName(entity.getProviderName());
        // 不返回原始 apiKey，仅返回脱敏值
        dto.setApiKeyMasked(maskApiKey(entity.getApiKey()));
        dto.setHasApiKey(entity.getApiKey() != null && !entity.getApiKey().isBlank());
        dto.setModelName(entity.getModelName());
        dto.setEnabled(entity.getEnabled());
        return dto;
    }

    /**
     * 对存储的 API Key 进行脱敏处理（保留前 3 位与后 4 位）
     *
     * @param storedKey 存储的 API Key（密文或明文）
     * @return 脱敏后的字符串，如 sk-****abcd；为空或解密失败时返回固定掩码
     */
    private String maskApiKey(String storedKey) {
        if (storedKey == null || storedKey.isBlank()) {
            return "";
        }
        String plain;
        try {
            plain = CryptoUtil.decrypt(storedKey);
        } catch (Exception e) {
            log.warn("API Key 脱敏时解密失败，返回固定掩码: {}", e.getMessage());
            return MASK;
        }
        if (plain == null || plain.length() <= MASK_MIN_PLAIN_LEN) {
            return MASK;
        }
        return plain.substring(0, MASK_PREFIX_LEN) + MASK + plain.substring(plain.length() - MASK_SUFFIX_LEN);
    }
}
