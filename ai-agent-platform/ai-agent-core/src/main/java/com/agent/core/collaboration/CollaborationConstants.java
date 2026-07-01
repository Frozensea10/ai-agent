package com.agent.core.collaboration;

public final class CollaborationConstants {

    private CollaborationConstants() {
    }

    public static final String AGENT_TYPE_MASTER = "master";
    public static final String AGENT_TYPE_SUB = "sub";

    public static final int AGENT_STATUS_ACTIVE = 1;

    public static final String FALLBACK_CAPABILITY = "code-execution";
    public static final String FALLBACK_TASK_ID = "1";
    public static final String FALLBACK_TASK_NAME = "直接处理";

    public static final String SYSTEM_PROMPT_LABEL = "系统提示：";
    public static final String TASK_LABEL = "任务：";
    public static final String RESULT_PROMPT = "请完成上述任务，并返回详细结果。";

    public static final long SUB_AGENT_TIMEOUT_SECONDS = 60L;
    public static final long COLLABORATION_TIMEOUT_SECONDS = 300L;
    public static final long SCHEDULE_POLL_INTERVAL_MS = 100L;
}
