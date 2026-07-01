package com.agent.chat.service;

import lombok.Getter;

@Getter
public enum MessageStatus {

    NORMAL(1, "正常"),
    DELETED(0, "已删除");

    private final int code;
    private final String desc;

    MessageStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
