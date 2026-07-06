package com.agent.core.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.utils.CryptoUtil;
import com.agent.core.dto.LlmProviderConfigDTO;
import com.agent.core.entity.LlmProviderConfig;
import com.agent.core.mapper.LlmProviderConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmProviderConfigService {

    private final LlmProviderConfigMapper configMapper;

    public List<LlmProviderConfigDTO> listConfigs() {
        List<LlmProviderConfig> entities = configMapper.selectList(
                new LambdaQueryWrapper<LlmProviderConfig>().orderByAsc(LlmProviderConfig::getProviderName)
        );
        return entities.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public LlmProviderConfigDTO getConfig(String providerName) {
        LlmProviderConfig entity = configMapper.selectOne(
                new LambdaQueryWrapper<LlmProviderConfig>()
                        .eq(LlmProviderConfig::getProviderName, providerName)
        );
        return entity == null ? null : convertToDTO(entity);
    }

    /**
     * 获取指定提供商的明文 API Key，供 LLMService 使用。
     * 若数据库中存储的是明文（历史数据），则读取时自动加密回写。
     */
    public String getApiKey(String providerName) {
        LlmProviderConfig entity = configMapper.selectOne(
                new LambdaQueryWrapper<LlmProviderConfig>()
                        .eq(LlmProviderConfig::getProviderName, providerName)
                        .eq(LlmProviderConfig::getEnabled, 1)
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
        entity.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
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

    private String maskApiKey(String storedKey) {
        if (storedKey == null || storedKey.isBlank()) {
            return "";
        }
        String plain;
        try {
            plain = CryptoUtil.decrypt(storedKey);
        } catch (Exception e) {
            log.warn("API Key 脱敏时解密失败，返回固定掩码: {}", e.getMessage());
            return "****";
        }
        if (plain == null || plain.length() <= 7) {
            return "****";
        }
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }
}
