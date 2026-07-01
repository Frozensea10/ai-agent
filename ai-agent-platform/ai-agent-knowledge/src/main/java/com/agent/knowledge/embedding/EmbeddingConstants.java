package com.agent.knowledge.embedding;

public final class EmbeddingConstants {

    private EmbeddingConstants() {
    }

    public static final String MODEL_TEXT_EMBEDDING_3_LARGE = "text-embedding-3-large";
    public static final String MODEL_TEXT_EMBEDDING_3_SMALL = "text-embedding-3-small";

    public static final int EMBEDDING_SIZE_3_LARGE = 3072;
    public static final int EMBEDDING_SIZE_3_SMALL = 1536;

    public static final int LOG_TEXT_MAX_LENGTH = 50;
}
