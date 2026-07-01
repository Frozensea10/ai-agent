package com.agent.knowledge.service;

public final class KnowledgeBaseConstants {

    private KnowledgeBaseConstants() {
    }

    public static final int ACTIVE_STATUS = 1;
    public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    public static final String CHUNK_ID_PREFIX = "chunk_";
    public static final String UNDERLINE = "_";

    public static final String PAYLOAD_CONTENT = "content";
    public static final String PAYLOAD_DOC_ID = "doc_id";
    public static final String PAYLOAD_CHUNK_INDEX = "chunk_index";
}
