CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_name VARCHAR(100) NOT NULL,
    kb_code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    embedding_model VARCHAR(100),
    embedding_provider VARCHAR(50) DEFAULT 'openai',
    document_count INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id BIGINT NOT NULL,
    doc_name VARCHAR(255) NOT NULL,
    doc_type VARCHAR(20),
    file_url VARCHAR(500),
    file_key VARCHAR(255),
    file_size BIGINT,
    chunk_count INT DEFAULT 0,
    vector_status TINYINT DEFAULT 0,
    error_message TEXT,
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_id BIGINT NOT NULL,
    kb_id BIGINT NOT NULL,
    chunk_id VARCHAR(100) NOT NULL,
    content TEXT,
    vector_id VARCHAR(100),
    chunk_index INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
