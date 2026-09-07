package com.agent.core.service;

import com.agent.core.dto.LlmProviderConfigDTO;

import java.util.List;

/**
 * LLM 提供商配置服务
 * 管理模型提供商配置的增删改查，API Key 加密存储、脱敏返回
 */
public interface LlmProviderConfigService {

    /**
     * 查询所有提供商配置（API Key 脱敏返回）
     *
     * @return 提供商配置 DTO 列表，按提供商名称升序
     */
    List<LlmProviderConfigDTO> listConfigs();

    /**
     * 根据提供商名称查询配置
     *
     * @param providerName 提供商名称
     * @return 提供商配置 DTO，不存在时返回 null
     */
    LlmProviderConfigDTO getConfig(String providerName);

    /**
     * 获取指定提供商的明文 API Key，供 LLMService 使用。
     * 若数据库中存储的是明文（历史数据），则读取时自动加密回写。
     *
     * @param providerName 提供商名称
     * @return 明文 API Key，未配置时返回 null
     */
    String getApiKey(String providerName);

    /**
     * 保存或更新提供商配置（按提供商名称判重，API Key 明文自动加密存储）
     *
     * @param dto 提供商配置参数
     */
    void saveConfig(LlmProviderConfigDTO dto);

    /**
     * 保存或更新提供商配置，并刷新对应提供商的模型缓存
     *
     * @param dto 提供商配置参数
     */
    void saveAndRefresh(LlmProviderConfigDTO dto);

    /**
     * 删除提供商配置
     *
     * @param id 配置 ID
     */
    void deleteConfig(Long id);
}
