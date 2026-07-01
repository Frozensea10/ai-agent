package com.agent.core.collaboration;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.core.entity.AgentConfig;
import com.agent.core.entity.SubAgentExecution;
import com.agent.core.llm.service.LLMService;
import com.agent.core.mapper.AgentConfigMapper;
import com.agent.core.mapper.SubAgentExecutionMapper;
import com.agent.core.metrics.AgentMetrics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.agent.core.collaboration.CollaborationConstants.*;

/**
 * Agent协作服务
 * 实现主从协作模式：主Agent分解任务，子Agent按依赖顺序并行执行，结果汇总
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCollaborationService {

    private final TaskPlanner taskPlanner;
    private final LLMService llmService;
    private final AgentConfigMapper agentConfigMapper;
    private final SubAgentExecutionMapper subAgentExecutionMapper;
    private final AgentMetrics agentMetrics;
    private final ObjectMapper objectMapper;

    // 使用Spring管理的线程池（有界线程池）
    @Resource
    @Qualifier("subAgentExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    /**
     * 执行多Agent协作
     */
    public CollaborationResult collaborate(Long masterAgentId, String userRequest, String sessionId) {
        long startTime = System.currentTimeMillis();
        CollaborationResult result = new CollaborationResult();
        result.setSessionId(sessionId);
        result.setMasterAgentId(masterAgentId);

        try {
            // 1. 获取主Agent配置
            AgentConfig masterAgent = agentConfigMapper.selectById(masterAgentId);
            if (masterAgent == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "主Agent不存在: " + masterAgentId);
            }

            if (!AGENT_TYPE_MASTER.equals(masterAgent.getAgentType())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该Agent不是主Agent类型: " + masterAgent.getAgentType());
            }

            // 2. 任务分解
            log.info("开始任务分解，主Agent: {}, 请求: {}", masterAgent.getAgentName(), userRequest);
            List<SubTask> subTasks = taskPlanner.decomposeTasks(userRequest, masterAgent);
            result.setSubTasks(subTasks);
            log.info("任务分解完成，共 {} 个子任务", subTasks.size());

            // 3. 获取关联的子Agent
            List<AgentConfig> subAgents = getSubAgents(masterAgentId);
            log.info("找到 {} 个可用子Agent", subAgents.size());

            // 4. 分配任务给子Agent
            Map<String, AgentConfig> taskAgentMap = assignTasksToAgents(subTasks, subAgents);

            // 5. 按依赖顺序执行子Agent任务
            executeSubTasksWithDependencies(subTasks, taskAgentMap, sessionId, masterAgentId);

            // 6. 汇总结果（过滤失败任务）
            String finalAnswer = aggregateResults(userRequest, subTasks, masterAgent);
            result.setFinalAnswer(finalAnswer);
            result.setSuccess(true);

            // 记录指标
            agentMetrics.recordCollaboration();
            agentMetrics.recordCollaborationTime(System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("多Agent协作执行失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 获取主Agent关联的所有子Agent
     */
    private List<AgentConfig> getSubAgents(Long masterAgentId) {
        return agentConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentConfig>()
                        .eq(AgentConfig::getParentAgentId, masterAgentId)
                        .eq(AgentConfig::getAgentType, AGENT_TYPE_SUB)
                        .eq(AgentConfig::getStatus, AGENT_STATUS_ACTIVE)
                        .orderByDesc(AgentConfig::getPriority)
        );
    }

    /**
     * 将任务分配给子Agent（基于能力匹配）
     */
    private Map<String, AgentConfig> assignTasksToAgents(List<SubTask> subTasks, List<AgentConfig> subAgents) {
        Map<String, AgentConfig> assignment = new ConcurrentHashMap<>();

        for (SubTask task : subTasks) {
            String requiredCapability = task.getRequiredCapability();

            AgentConfig matchedAgent = subAgents.stream()
                    .filter(agent -> hasCapability(agent, requiredCapability))
                    .findFirst()
                    .orElse(null);

            if (matchedAgent != null) {
                assignment.put(task.getTaskId(), matchedAgent);
                task.setAssignedAgentId(matchedAgent.getId());
                log.info("任务 {} 分配给子Agent {} (能力: {})",
                        task.getTaskId(), matchedAgent.getAgentName(), requiredCapability);
            } else {
                log.warn("未找到匹配能力 {} 的子Agent，任务 {} 将使用主Agent执行",
                        requiredCapability, task.getTaskId());
            }
        }

        return assignment;
    }

    /**
     * 检查Agent是否具有指定能力
     */
    private boolean hasCapability(AgentConfig agent, String capability) {
        if (agent.getCapabilities() == null || agent.getCapabilities().isEmpty()) {
            return false;
        }
        try {
            List<String> capabilities = objectMapper.readValue(agent.getCapabilities(), new TypeReference<List<String>>() {});
            return capabilities.contains(capability);
        } catch (Exception e) {
            log.warn("解析Agent能力标签失败: {}", agent.getCapabilities());
            return false;
        }
    }

    /**
     * 按依赖顺序执行子任务（拓扑排序 + 并行执行无依赖任务）
     */
    private void executeSubTasksWithDependencies(List<SubTask> subTasks, Map<String, AgentConfig> taskAgentMap,
                                                  String sessionId, Long masterAgentId) {
        // 构建任务ID到任务映射
        Map<String, SubTask> taskMap = subTasks.stream()
                .collect(Collectors.toMap(SubTask::getTaskId, t -> t));

        // 计算每个任务的入度（依赖数）
        Map<String, Integer> inDegree = new ConcurrentHashMap<>();
        for (SubTask task : subTasks) {
            inDegree.put(task.getTaskId(), task.getDependsOn() != null ? task.getDependsOn().size() : 0);
        }

        // 记录已完成的任务
        Set<String> completedTasks = ConcurrentHashMap.newKeySet();

        // 使用CompletableFuture跟踪任务执行
        Map<String, CompletableFuture<Void>> taskFutures = new ConcurrentHashMap<>();

        long overallStartTime = System.currentTimeMillis();

        // 循环执行直到所有任务完成或超时
        while (completedTasks.size() < subTasks.size()) {
            // 检查整体超时
            if (System.currentTimeMillis() - overallStartTime > TimeUnit.SECONDS.toMillis(COLLABORATION_TIMEOUT_SECONDS)) {
                log.error("多Agent协作整体超时，已完成 {}/{} 个任务", completedTasks.size(), subTasks.size());
                break;
            }

            // 找出当前可以执行的任务（入度为0且未执行）
            List<SubTask> readyTasks = subTasks.stream()
                    .filter(t -> !completedTasks.contains(t.getTaskId()))
                    .filter(t -> !taskFutures.containsKey(t.getTaskId()))
                    .filter(t -> inDegree.getOrDefault(t.getTaskId(), 0) == 0)
                    .collect(Collectors.toList());

            if (readyTasks.isEmpty()) {
                // 检查是否还有正在执行的任务
                boolean hasRunning = taskFutures.values().stream().anyMatch(f -> !f.isDone());
                if (!hasRunning) {
                    log.error("任务调度死锁，无法继续执行");
                    break;
                }
                // 等待一小段时间再检查
                try {
                    Thread.sleep(SCHEDULE_POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            // 并行启动所有就绪任务
            for (SubTask task : readyTasks) {
                AgentConfig subAgent = taskAgentMap.get(task.getTaskId());

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    executeSingleTask(task, subAgent, sessionId, masterAgentId);
                    completedTasks.add(task.getTaskId());

                    // 减少依赖此任务的其他任务的入度
                    if (task.getDependents() != null) {
                        for (SubTask dependent : task.getDependents()) {
                            inDegree.computeIfPresent(dependent.getTaskId(), (k, v) -> v - 1);
                        }
                    }
                }, taskExecutor);

                taskFutures.put(task.getTaskId(), future);
            }
        }

        // 等待所有已启动的任务完成
        try {
            CompletableFuture.allOf(
                    taskFutures.values().toArray(new CompletableFuture[0])
            ).get(SUB_AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("子任务执行超时或失败", e);
        }
    }

    /**
     * 执行单个任务
     */
    private void executeSingleTask(SubTask task, AgentConfig subAgent, String sessionId, Long masterAgentId) {
        long taskStart = System.currentTimeMillis();
        task.setStatus(TaskStatus.RUNNING.getCode());

        SubAgentExecution execution = new SubAgentExecution();
        execution.setSessionId(sessionId);
        execution.setMasterAgentId(masterAgentId);
        execution.setSubAgentId(subAgent != null ? subAgent.getId() : null);
        execution.setTaskDescription(task.getTaskDescription());
        execution.setTaskStatus(TaskStatus.RUNNING.getCode());
        subAgentExecutionMapper.insert(execution);

        try {
            String result;
            if (subAgent != null) {
                result = executeWithSubAgent(task, subAgent);
            } else {
                result = "任务未分配Agent: " + task.getTaskDescription();
            }

            task.setResult(result);
            task.setStatus(TaskStatus.SUCCESS.getCode());
            task.setExecutionTimeMs(System.currentTimeMillis() - taskStart);

            execution.setTaskResult(result);
            execution.setTaskStatus(TaskStatus.SUCCESS.getCode());
            execution.setExecutionTimeMs((int) (System.currentTimeMillis() - taskStart));
            subAgentExecutionMapper.updateById(execution);

            log.info("任务 {} 执行成功，耗时 {}ms", task.getTaskId(), task.getExecutionTimeMs());

        } catch (Exception e) {
            log.error("任务 {} 执行失败", task.getTaskId(), e);
            task.setStatus(TaskStatus.FAILED.getCode());
            task.setResult("执行失败: " + e.getMessage());
            task.setExecutionTimeMs(System.currentTimeMillis() - taskStart);

            execution.setTaskResult(task.getResult());
            execution.setTaskStatus(TaskStatus.FAILED.getCode());
            execution.setExecutionTimeMs((int) (System.currentTimeMillis() - taskStart));
            subAgentExecutionMapper.updateById(execution);
        }
    }

    /**
     * 使用子Agent执行任务
     */
    private String executeWithSubAgent(SubTask task, AgentConfig subAgent) {
        ChatModel chatModel = llmService.createChatModel(
                subAgent.getModelProvider(),
                subAgent.getModelName(),
                subAgent.getTemperature(),
                subAgent.getMaxTokens()
        );

        String prompt = buildSubAgentPrompt(subAgent, task);
        return chatModel.chat(prompt);
    }

    /**
     * 构建子Agent提示词
     */
    private String buildSubAgentPrompt(AgentConfig subAgent, SubTask task) {
        StringBuilder prompt = new StringBuilder();

        if (subAgent.getSystemPrompt() != null && !subAgent.getSystemPrompt().isEmpty()) {
            prompt.append(SYSTEM_PROMPT_LABEL).append(subAgent.getSystemPrompt()).append("\n\n");
        }

        prompt.append(TASK_LABEL).append(task.getTaskDescription()).append("\n\n");
        prompt.append(RESULT_PROMPT);

        return prompt.toString();
    }

    /**
     * 汇总所有子任务结果，生成最终回答（过滤失败任务）
     */
    private String aggregateResults(String userRequest, List<SubTask> subTasks, AgentConfig masterAgent) {
        StringBuilder resultSummary = new StringBuilder();
        resultSummary.append("用户请求：").append(userRequest).append("\n\n");
        resultSummary.append("各子任务执行结果：\n\n");

        boolean hasSuccess = false;
        for (SubTask task : subTasks) {
            if (TaskStatus.SUCCESS.getCode().equals(task.getStatus())) {
                hasSuccess = true;
                resultSummary.append("【").append(task.getTaskName()).append("】\n");
                resultSummary.append("状态：").append(task.getStatus()).append("\n");
                resultSummary.append("结果：").append(task.getResult() != null ? task.getResult() : "无结果").append("\n\n");
            } else {
                resultSummary.append("【").append(task.getTaskName()).append("】\n");
                resultSummary.append("状态：").append(task.getStatus()).append(" (执行失败，已跳过)").append("\n\n");
            }
        }

        if (!hasSuccess) {
            return "所有子任务执行失败，请检查Agent配置和工具状态。";
        }

        resultSummary.append("请基于以上各子任务的结果，为用户提供一份完整、连贯的最终回答。");

        ChatModel chatModel = llmService.createChatModel(
                masterAgent.getModelProvider(),
                masterAgent.getModelName(),
                masterAgent.getTemperature(),
                masterAgent.getMaxTokens()
        );

        return chatModel.chat(resultSummary.toString());
    }

    /**
     * 获取子Agent执行历史
     */
    public List<SubAgentExecution> getExecutionHistory(String sessionId) {
        return subAgentExecutionMapper.selectBySessionId(sessionId);
    }
}
