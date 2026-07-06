package com.agent.knowledge.mapper;

import com.agent.knowledge.entity.KnowledgeBase;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    @Select("SELECT * FROM knowledge_base WHERE status = 1 AND deleted = 0 ORDER BY created_at DESC")
    List<KnowledgeBase> selectAllActive();

    @Select("SELECT * FROM knowledge_base WHERE created_by = #{userId} AND status = 1 AND deleted = 0 ORDER BY created_at DESC")
    List<KnowledgeBase> selectByUserId(Long userId);

    @Select("SELECT * FROM knowledge_base WHERE kb_code = #{kbCode} LIMIT 1")
    KnowledgeBase selectByCode(String kbCode);

    @Update("UPDATE knowledge_base SET document_count = document_count + 1 WHERE id = #{kbId}")
    int incrementDocumentCount(Long kbId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM knowledge_base WHERE id = #{id}")
    int physicalDeleteById(Long id);
}
