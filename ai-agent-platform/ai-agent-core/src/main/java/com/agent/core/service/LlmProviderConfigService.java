package com.agent.core.service;

import com.agent.core.dto.LlmProviderConfigDTO;
import com.agent.core.entity.LlmProviderConfig;
import com.agent.core.mapper.LlmProviderConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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

    public String getApiKey(String providerName) {
        LlmProviderConfig entity = configMapper.selectOne(
                new LambdaQueryWrapper<LlmProviderConfig>()
                        .eq(LlmProviderConfig::getProviderName, providerName)
                        .eq(LlmProviderConfig::getEnabled, 1)
        );
        return entity == null ? null : entity.getApiKey();
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(LlmProviderConfigDTO dto) {
        LlmProviderConfig existing = configMapper.selectOne(
                new LambdaQueryWrapper<LlmProviderConfig>()
                        .eq(LlmProviderConfig::getProviderName, dto.getProviderName())
        );
        LlmProviderConfig entity = new LlmProviderConfig();
        entity.setProviderName(dto.getProviderName());
        entity.setApiKey(dto.getApiKey());
        entity.setModelName(dto.getModelName());
        entity.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        if (existing != null) {
            entity.setId(existing.getId());
            configMapper.updateById(entity);
        } else {
            configMapper.insert(entity);
        }
    }

    private LlmProviderConfigDTO convertToDTO(LlmProviderConfig entity) {
        LlmProviderConfigDTO dto = new LlmProviderConfigDTO();
        dto.setProviderName(entity.getProviderName());
        dto.setApiKey(entity.getApiKey());
        dto.setModelName(entity.getModelName());
        dto.setEnabled(entity.getEnabled());
        return dto;
    }
}
