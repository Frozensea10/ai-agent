package com.agent.chat.mapper;

import com.agent.chat.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} AND status = 1 AND deleted = 0 ORDER BY created_at ASC")
    List<ChatMessage> selectBySessionId(String sessionId);
}
