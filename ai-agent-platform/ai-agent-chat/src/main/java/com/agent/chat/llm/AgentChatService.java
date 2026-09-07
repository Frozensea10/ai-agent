package com.agent.chat.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Agent 对话服务接口
 * 基于 LangChain4j AiServices 实现声明式 Agent，支持原生 function calling
 */
public interface AgentChatService {

    /**
     * 执行对话
     *
     * @param message      用户消息
     * @param systemPrompt 系统提示词
     * @param ragContext   RAG 检索上下文
     * @return 助手回答
     */
    @SystemMessage("""
        {{systemPrompt}}

        {{ragContext}}

        你可以使用以下工具来帮助回答用户问题。当需要调用工具时，系统会自动处理。
        请基于工具返回的结果给出完整、准确的回答。
        """)
    String chat(@UserMessage String message,
                @V("systemPrompt") String systemPrompt,
                @V("ragContext") String ragContext);

    /**
     * 执行流式对话（支持工具调用，工具执行完成后继续生成最终回复）
     *
     * @param message      用户消息
     * @param systemPrompt 系统提示词
     * @param ragContext   RAG 检索上下文
     * @return TokenStream 流式句柄
     */
    @SystemMessage("""
        {{systemPrompt}}

        {{ragContext}}

        你可以使用以下工具来帮助回答用户问题。当需要调用工具时，系统会自动处理。
        请基于工具返回的结果给出完整、准确的回答。
        """)
    TokenStream streamChat(@UserMessage String message,
                           @V("systemPrompt") String systemPrompt,
                           @V("ragContext") String ragContext);
}
