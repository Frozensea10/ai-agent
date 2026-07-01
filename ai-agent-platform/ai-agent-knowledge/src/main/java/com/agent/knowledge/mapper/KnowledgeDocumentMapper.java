package com.agent.knowledge.mapper;

import com.agent.knowledge.entity.KnowledgeDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    @Select("SELECT * FROM knowledge_document WHERE kb_id = #{kbId} AND status = 1 AND deleted = 0 ORDER BY created_at DESC")
    List<KnowledgeDocument> selectByKbId(Long kbId);
}
