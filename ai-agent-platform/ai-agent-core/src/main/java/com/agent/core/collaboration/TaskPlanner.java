package com.agent.core.collaboration;

import com.agent.core.entity.AgentConfig;
import com.agent.core.llm.service.LLMService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.agent.core.collaboration.CollaborationConstants.*;

/**
 * 任务规划器
 * 使用LLM将用户请求分解为可执行的子任务列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPlanner {

    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    private static final String TASK_DECOMPOSITION_PROMPT = """
        你是一个任务规划专家。请将用户的请求分解为多个可并行执行的子任务。
        
        可用子Agent能力标签：
        - code-execution: 执行Python/Java代码
        - data-analysis: 数据分析、统计计算
        - web-search: 网络搜索、信息检索
        - knowledge-retrieval: 知识库检索、文档查询
        - report-generation: 报告生成、文档撰写
        
        请按以下JSON格式输出子任务列表：
        [
          {
            "taskId": "1",
            "taskName": "任务名称",
            "taskDescription": "详细任务描述",
            "requiredCapability": "所需能力标签",
            "dependsOn": [] // 依赖的其他任务ID
          }
        ]
        
        注意：
        1. 任务应尽量独立，可并行执行
        2. 如果任务之间有依赖关系，请在dependsOn中标注
        3. 每个任务必须对应一个可用能力标签
        4. 任务描述应足够详细，子Agent可直接执行
        
        用户请求：%s
        """;

    private static final String JSON_START_ARRAY = "[";
    private static final String JSON_END_ARRAY = "]";
    private static final String TASK_PATTERN = ".*\\d+.*[:：].*";
    private static final String TASK_PREFIX_REPLACEMENT = ".*\\d+[:：]\\s*";
    private static final Pattern TASK_LINE_PATTERN = Pattern.compile(".*\\d+.*[:：].*");

    /**
     * 分解任务
     *
     * @param userRequest 用户请求
     * @param masterAgent 主Agent配置
     * @return 子任务列表
     */
    public List<SubTask> decomposeTasks(String userRequest, AgentConfig masterAgent) {
        try {
            ChatModel chatModel = llmService.createChatModel(
                    masterAgent.getModelProvider(),
                    masterAgent.getModelName(),
                    masterAgent.getTemperature(),
                    masterAgent.getMaxTokens()
            );

            String prompt = String.format(TASK_DECOMPOSITION_PROMPT, userRequest);
            String response = chatModel.chat(prompt);

            log.info("任务分解结果: {}", response);

            // 解析JSON响应
            List<SubTask> tasks = parseTasks(response);
            
            // 设置任务依赖关系
            buildDependencyGraph(tasks);
            
            return tasks;

        } catch (Exception e) {
            log.error("任务分解失败", e);
            // 降级策略：返回单个任务
            return createFallbackTask(userRequest);
        }
    }

    /**
     * 解析LLM返回的任务列表
     */
    private List<SubTask> parseTasks(String response) {
        try {
            // 提取JSON部分
            String json = extractJson(response);
            return objectMapper.readValue(json, new TypeReference<List<SubTask>>() {});
        } catch (Exception e) {
            log.warn("解析任务JSON失败，尝试备用解析: {}", response);
            return parseTasksFallback(response);
        }
    }

    /**
     * 从响应中提取JSON内容
     */
    private String extractJson(String response) {
        String trimmed = response.trim();
        
        // 如果整个响应是JSON
        if (trimmed.startsWith(JSON_START_ARRAY) && trimmed.endsWith(JSON_END_ARRAY)) {
            return trimmed;
        }
        
        // 提取代码块中的JSON
        int start = trimmed.indexOf(JSON_START_ARRAY);
        int end = trimmed.lastIndexOf(JSON_END_ARRAY);
        
        if (start != -1 && end != -1 && start < end) {
            return trimmed.substring(start, end + 1);
        }
        
        return trimmed;
    }

    /**
     * 备用解析：当JSON解析失败时使用简单解析
     */
    private List<SubTask> parseTasksFallback(String response) {
        List<SubTask> tasks = new ArrayList<>();
        
        // 简单按行解析，尝试提取任务信息
        String[] lines = response.split("\n");
        int taskId = 1;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            Matcher matcher = TASK_LINE_PATTERN.matcher(line);
            // 尝试匹配任务描述模式
            if (matcher.matches()) {
                SubTask task = new SubTask();
                task.setTaskId(String.valueOf(taskId++));
                task.setTaskName(line.replaceAll(TASK_PREFIX_REPLACEMENT, ""));
                task.setTaskDescription(line);
                task.setRequiredCapability(FALLBACK_CAPABILITY);
                task.setDependsOn(new ArrayList<>());
                tasks.add(task);
            }
        }
        
        if (tasks.isEmpty()) {
            return createFallbackTask(response);
        }
        
        return tasks;
    }

    /**
     * 构建任务依赖图
     */
    private void buildDependencyGraph(List<SubTask> tasks) {
        for (SubTask task : tasks) {
            if (task.getDependsOn() != null) {
                for (String depId : task.getDependsOn()) {
                    tasks.stream()
                            .filter(t -> t.getTaskId().equals(depId))
                            .findFirst()
                            .ifPresent(dep -> dep.addDependent(task));
                }
            }
        }
    }

    /**
     * 创建降级任务（当分解失败时）
     */
    private List<SubTask> createFallbackTask(String userRequest) {
        List<SubTask> tasks = new ArrayList<>();
        SubTask task = new SubTask();
        task.setTaskId(FALLBACK_TASK_ID);
        task.setTaskName(FALLBACK_TASK_NAME);
        task.setTaskDescription(userRequest);
        task.setRequiredCapability(FALLBACK_CAPABILITY);
        task.setDependsOn(new ArrayList<>());
        tasks.add(task);
        return tasks;
    }
}
