package com.agent.knowledge.mapper;

import com.agent.knowledge.entity.KnowledgeChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    @Select("SELECT * FROM knowledge_chunk WHERE doc_id = #{docId} AND status = 1 AND deleted = 0 ORDER BY chunk_index ASC")
    List<KnowledgeChunk> selectByDocId(Long docId);

    @Select("SELECT * FROM knowledge_chunk WHERE kb_id = #{kbId} AND status = 1 AND deleted = 0")
    List<KnowledgeChunk> selectByKbId(Long kbId);
}
