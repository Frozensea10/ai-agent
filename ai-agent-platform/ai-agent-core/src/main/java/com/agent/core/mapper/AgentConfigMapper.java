package com.agent.core.mapper;

import com.agent.core.entity.AgentConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentConfigMapper extends BaseMapper<AgentConfig> {

    @Select("SELECT * FROM agent_config WHERE status = 1 AND deleted = 0")
    List<AgentConfig> selectAllActive();

    @Select("SELECT * FROM agent_config WHERE agent_code = #{agentCode} AND status = 1 AND deleted = 0 LIMIT 1")
    AgentConfig selectByCode(String agentCode);
}
