package com.agent.chat.feign;

import com.agent.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class KnowledgeFeignClientFallbackFactory implements FallbackFactory<KnowledgeFeignClient> {

    @Override
    public KnowledgeFeignClient create(Throwable cause) {
        log.error("Knowledge服务调用失败", cause);
        return new KnowledgeFeignClient() {
            @Override
            public Result<KnowledgeBaseVO> getKnowledgeBaseById(Long id, Long userId) {
                return Result.error("Knowledge服务不可用");
            }

            @Override
            public Result<List<RetrievalResult>> retrieve(String kbCode, RetrieveRequest request) {
                return Result.error("Knowledge服务不可用");
            }

            @Override
            public Result<RAGResponse> ragQuery(String kbCode, RAGQueryRequest request, Long userId) {
                return Result.error("Knowledge服务不可用");
            }
        };
    }
}
