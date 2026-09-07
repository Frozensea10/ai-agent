package com.agent.chat.llm;

import com.agent.chat.dto.SSEMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import reactor.core.publisher.Flux;

public interface ChatLLMService {

    String chat(String sessionId, String message, String systemPrompt,
                String ragContext, String memoryType, Integer maxMessages,
                ChatModel chatModel, Long userId);

    Flux<SSEMessage> streamChat(String sessionId, String message, String systemPrompt,
                                String ragContext, String memoryType, Integer maxMessages,
                                StreamingChatModel streamingModel, ChatModel chatModel,
                                String modelName, Long userId);
}
