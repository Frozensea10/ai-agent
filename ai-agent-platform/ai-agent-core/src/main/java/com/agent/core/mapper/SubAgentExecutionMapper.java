package com.agent.core.mapper;

import com.agent.core.entity.SubAgentExecution;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SubAgentExecutionMapper extends BaseMapper<SubAgentExecution> {

    @Select("SELECT * FROM sub_agent_execution WHERE session_id = #{sessionId} ORDER BY created_at ASC")
    List<SubAgentExecution> selectBySessionId(String sessionId);

    @Select("SELECT * FROM sub_agent_execution WHERE master_agent_id = #{masterAgentId} ORDER BY created_at DESC")
    List<SubAgentExecution> selectByMasterAgentId(Long masterAgentId);
}
