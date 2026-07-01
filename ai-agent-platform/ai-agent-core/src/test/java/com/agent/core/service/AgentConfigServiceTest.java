package com.agent.core.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.core.dto.CreateAgentRequest;
import com.agent.core.dto.UpdateAgentRequest;
import com.agent.core.entity.AgentConfig;
import com.agent.core.mapper.AgentConfigMapper;
import com.agent.core.vo.AgentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentConfigServiceTest {

    @Mock
    private AgentConfigMapper agentConfigMapper;

    @InjectMocks
    private AgentConfigService agentConfigService;

    private CreateAgentRequest createRequest;

    @BeforeEach
    void setUp() {
        createRequest = new CreateAgentRequest();
        createRequest.setAgentName("测试助手");
        createRequest.setAgentCode("test-assistant");
        createRequest.setDescription("用于测试的 Agent");
        createRequest.setModelProvider("openai");
        createRequest.setModelName("gpt-4o-mini");
        createRequest.setSystemPrompt("你是一个测试助手");
        createRequest.setTemperature(0.5);
        createRequest.setMaxTokens(1024);
    }

    @Test
    @DisplayName("创建 Agent 成功并填充默认值")
    void shouldCreateAgentWithDefaults() {
        when(agentConfigMapper.selectByCode("test-assistant")).thenReturn(null);
        when(agentConfigMapper.insert(ArgumentMatchers.<AgentConfig>any())).thenReturn(1);

        AgentVO vo = agentConfigService.createAgent(createRequest, 1L);

        assertNotNull(vo);
        assertEquals("测试助手", vo.getAgentName());
        assertEquals("test-assistant", vo.getAgentCode());
        assertEquals("window", vo.getMemoryType());
        assertEquals(10, vo.getMemoryMaxMessages());
        assertEquals("single", vo.getAgentType());
        assertEquals(0, vo.getPriority());
        assertEquals(1, vo.getStatus());
    }

    @Test
    @DisplayName("创建 Agent 时编码重复抛出参数错误")
    void shouldThrowWhenAgentCodeExists() {
        when(agentConfigMapper.selectByCode("test-assistant")).thenReturn(new AgentConfig());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> agentConfigService.createAgent(createRequest, 1L));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(agentConfigMapper, never()).insert(ArgumentMatchers.<AgentConfig>any());
    }

    @Test
    @DisplayName("更新 Agent 时仅设置非空字段")
    void shouldUpdateAgentPartially() {
        AgentConfig existing = new AgentConfig();
        existing.setId(1L);
        existing.setAgentName("旧名称");
        existing.setAgentCode("test-assistant");
        existing.setDescription("旧描述");
        existing.setModelProvider("openai");
        existing.setModelName("gpt-4o-mini");
        existing.setTemperature(0.5);
        existing.setMaxTokens(1024);
        existing.setStatus(1);
        existing.setCreatedBy(1L);

        when(agentConfigMapper.selectById(1L)).thenReturn(existing);
        when(agentConfigMapper.updateById(existing)).thenReturn(1);

        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setAgentName("新名称");

        AgentVO vo = agentConfigService.updateAgent(1L, request, 1L);

        assertEquals("新名称", vo.getAgentName());
        assertEquals("旧描述", vo.getDescription());
        assertEquals("openai", vo.getModelProvider());
        verify(agentConfigMapper).updateById(existing);
    }

    @Test
    @DisplayName("更新不存在的 Agent 抛出 NOT_FOUND")
    void shouldThrowWhenUpdateAgentNotFound() {
        when(agentConfigMapper.selectById(1L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> agentConfigService.updateAgent(1L, new UpdateAgentRequest(), 1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("更新非本人 Agent 抛出 FORBIDDEN")
    void shouldThrowWhenUpdateAgentNotOwner() {
        AgentConfig existing = new AgentConfig();
        existing.setId(1L);
        existing.setCreatedBy(2L);

        when(agentConfigMapper.selectById(1L)).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> agentConfigService.updateAgent(1L, new UpdateAgentRequest(), 1L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(agentConfigMapper, never()).updateById(ArgumentMatchers.<AgentConfig>any());
    }

    @Test
    @DisplayName("根据 ID 查询 Agent 成功")
    void shouldGetAgentById() {
        AgentConfig config = new AgentConfig();
        config.setId(1L);
        config.setAgentName("测试助手");
        config.setAgentCode("test-assistant");
        config.setStatus(1);
        config.setCreatedBy(1L);

        when(agentConfigMapper.selectById(1L)).thenReturn(config);

        AgentVO vo = agentConfigService.getAgentById(1L, 1L);

        assertEquals("测试助手", vo.getAgentName());
        assertEquals("test-assistant", vo.getAgentCode());
    }

    @Test
    @DisplayName("查询不存在的 Agent 抛出 NOT_FOUND")
    void shouldThrowWhenAgentNotFound() {
        when(agentConfigMapper.selectById(1L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> agentConfigService.getAgentById(1L, 1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("查询非本人 Agent 抛出 FORBIDDEN")
    void shouldThrowWhenGetAgentNotOwner() {
        AgentConfig config = new AgentConfig();
        config.setId(1L);
        config.setCreatedBy(2L);

        when(agentConfigMapper.selectById(1L)).thenReturn(config);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> agentConfigService.getAgentById(1L, 1L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("查询当前用户生效 Agent 列表")
    void shouldListActiveAgents() {
        AgentConfig config1 = new AgentConfig();
        config1.setId(1L);
        config1.setAgentName("Agent1");
        config1.setAgentCode("agent-1");
        config1.setStatus(1);
        config1.setCreatedBy(1L);

        AgentConfig config2 = new AgentConfig();
        config2.setId(2L);
        config2.setAgentName("Agent2");
        config2.setAgentCode("agent-2");
        config2.setStatus(1);
        config2.setCreatedBy(1L);

        when(agentConfigMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<AgentConfig>>any()))
                .thenReturn(List.of(config1, config2));

        List<AgentVO> result = agentConfigService.listAgents(1L);

        assertEquals(2, result.size());
        assertEquals("Agent1", result.get(0).getAgentName());
        assertEquals("Agent2", result.get(1).getAgentName());
    }

    @Test
    @DisplayName("删除 Agent 成功")
    void shouldDeleteAgent() {
        AgentConfig config = new AgentConfig();
        config.setId(1L);
        config.setCreatedBy(1L);
        when(agentConfigMapper.selectById(1L)).thenReturn(config);

        agentConfigService.deleteAgent(1L, 1L);

        verify(agentConfigMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除非本人 Agent 抛出 FORBIDDEN")
    void shouldThrowWhenDeleteAgentNotOwner() {
        AgentConfig config = new AgentConfig();
        config.setId(1L);
        config.setCreatedBy(2L);
        when(agentConfigMapper.selectById(1L)).thenReturn(config);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> agentConfigService.deleteAgent(1L, 1L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(agentConfigMapper, never()).deleteById(1L);
    }
}
