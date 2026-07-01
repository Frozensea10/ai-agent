package com.agent.chat.service;

public final class MessageIdGenerator {

    private MessageIdGenerator() {
    }

    private static final String MESSAGE_ID_PREFIX = "msg_";
    private static final int MESSAGE_ID_LENGTH = 16;
    private static final String DASH = "-";
    private static final String EMPTY = "";

    public static String generate() {
        String uuid = java.util.UUID.randomUUID().toString().replace(DASH, EMPTY);
        if (uuid.length() > MESSAGE_ID_LENGTH) {
            uuid = uuid.substring(0, MESSAGE_ID_LENGTH);
        }
        return MESSAGE_ID_PREFIX + uuid;
    }
}
