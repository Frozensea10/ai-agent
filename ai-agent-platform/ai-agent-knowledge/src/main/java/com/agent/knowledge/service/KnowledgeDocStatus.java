package com.agent.knowledge.service;

public enum KnowledgeDocStatus {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    EMBEDDING(2, "嵌入中"),
    COMPLETED(3, "已完成"),
    FAILED(4, "处理失败");

    private final int code;
    private final String desc;

    KnowledgeDocStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
