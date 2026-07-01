package com.agent.chat.service;

import lombok.Getter;

@Getter
public enum MessageRole {

    USER("user", "用户"),
    ASSISTANT("assistant", "助手"),
    SYSTEM("system", "系统");

    private final String code;
    private final String desc;

    MessageRole(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
