package com.agent.chat.mapper;

import com.agent.chat.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} AND status = 1 AND deleted = 0 ORDER BY updated_at DESC")
    List<ChatSession> selectByUserId(Long userId);

    @Select("SELECT * FROM chat_session WHERE session_id = #{sessionId} AND status = 1 AND deleted = 0 LIMIT 1")
    ChatSession selectBySessionId(String sessionId);

    @Update("UPDATE chat_session SET message_count = message_count + 1, updated_at = NOW() WHERE session_id = #{sessionId}")
    void incrementMessageCount(String sessionId);
}
