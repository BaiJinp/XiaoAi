package com.xiaoai.agent.skill.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.skill.entity.Skill;
import com.xiaoai.agent.skill.model.CreateSkillCommand;
import com.xiaoai.agent.skill.service.SkillService;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskRun;
import com.xiaoai.agent.task.entity.TaskStep;
import com.xiaoai.agent.task.service.TaskRunService;
import com.xiaoai.agent.task.service.TaskService;
import com.xiaoai.agent.task.service.TaskStepService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能提取器
 * 从成功的任务中自动提取可复用的技能
 *
 * 这是 Hermes Agent 自我学习进化的核心组件
 */
@Component
public class SkillExtractor {

    private static final Logger log = LoggerFactory.getLogger(SkillExtractor.class);

    private final SkillService skillService;
    private final TaskService taskService;
    private final TaskRunService taskRunService;
    private final TaskStepService taskStepService;
    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;

    // 默认模型ID（可配置）
    private static final Long DEFAULT_MODEL_ID = 1L;

    @Autowired
    public SkillExtractor(SkillService skillService,
                         TaskService taskService,
                         TaskRunService taskRunService,
                         TaskStepService taskStepService,
                         ModelGateway modelGateway,
                         ObjectMapper objectMapper) {
        this.skillService = skillService;
        this.taskService = taskService;
        this.taskRunService = taskRunService;
        this.taskStepService = taskStepService;
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
    }

    /**
     * 从任务中提取技能
     *
     * @param tenantId 租户ID
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 提取的技能列表
     */
    public List<Skill> extractFromTask(Long tenantId, Long taskId, Long userId) {
        log.info("Starting skill extraction from task: tenantId={}, taskId={}", tenantId, taskId);

        // 1. 获取任务信息
        Task task = taskService.getById(taskId);
        if (task == null) {
            log.warn("Task not found: id={}", taskId);
            return List.of();
        }

        // 2. 检查任务是否成功完成
        if (!"completed".equals(task.getStatus())) {
            log.info("Task not completed, skip skill extraction: id={}, status={}",
                    taskId, task.getStatus());
            return List.of();
        }

        // 3. 获取任务执行记录
        List<TaskRun> runs = taskRunService.list(new LambdaQueryWrapper<TaskRun>()
                .eq(TaskRun::getTaskId, taskId)
                .eq(TaskRun::getStatus, "completed")
                .orderByAsc(TaskRun::getCreatedAt));

        if (runs.isEmpty()) {
            log.info("No completed runs found for task: id={}", taskId);
            return List.of();
        }

        // 4. 获取任务步骤
        List<TaskStep> steps = taskStepService.list(new LambdaQueryWrapper<TaskStep>()
                .eq(TaskStep::getTaskId, taskId)
                .eq(TaskStep::getStatus, "completed")
                .orderByAsc(TaskStep::getSortOrder));

        // 5. 构建任务执行上下文
        String taskContext = buildTaskContext(task, runs, steps);

        // 6. 调用模型分析任务，提取技能
        List<Skill> extractedSkills = analyzeAndExtractSkills(tenantId, taskId, userId, taskContext);

        log.info("Skill extraction completed: taskId={}, extractedCount={}",
                taskId, extractedSkills.size());

        return extractedSkills;
    }

    /**
     * 构建任务执行上下文
     */
    private String buildTaskContext(Task task, List<TaskRun> runs, List<TaskStep> steps) {
        try {
            ObjectNode context = objectMapper.createObjectNode();

            // 任务基本信息
            context.put("taskType", task.getTaskType());
            context.put("inputText", task.getInputText());
            context.put("resultSummary", task.getResultSummary());

            // 执行步骤
            ArrayNode stepsArray = objectMapper.createArrayNode();
            for (TaskStep step : steps) {
                ObjectNode stepNode = objectMapper.createObjectNode();
                stepNode.put("stepType", step.getStepType());
                stepNode.put("stepName", step.getStepName());
                stepNode.put("status", step.getStatus());
                if (StringUtils.hasText(step.getInputJson())) {
                    stepNode.set("input", objectMapper.readTree(step.getInputJson()));
                }
                if (StringUtils.hasText(step.getOutputJson())) {
                    stepNode.set("output", objectMapper.readTree(step.getOutputJson()));
                }
                stepsArray.add(stepNode);
            }
            context.set("steps", stepsArray);

            // 执行次数
            context.put("runCount", runs.size());

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);

        } catch (Exception e) {
            log.error("Failed to build task context", e);
            return "{}";
        }
    }

    /**
     * 调用模型分析任务并提取技能
     */
    private List<Skill> analyzeAndExtractSkills(Long tenantId, Long taskId, Long userId, String taskContext) {
        List<Skill> skills = new ArrayList<>();

        try {
            // 构建提取提示词
            String prompt = buildExtractionPrompt(taskContext);

            // 调用模型
            ChatModelCommand command = new ChatModelCommand();
            command.setModelId(DEFAULT_MODEL_ID);
            command.setTaskId(taskId);
            command.setPrompt(prompt);

            ChatModelResponse response = modelGateway.chat(command);
            String analysisResult = response.getContent();

            // 解析模型返回的技能列表
            skills = parseExtractedSkills(tenantId, taskId, userId, analysisResult);

            // 保存提取的技能
            for (Skill skill : skills) {
                CreateSkillCommand createCommand = new CreateSkillCommand();
                createCommand.setTenantId(skill.getTenantId());
                createCommand.setSkillCode(skill.getSkillCode());
                createCommand.setSkillName(skill.getSkillName());
                createCommand.setDescription(skill.getDescription());
                createCommand.setSkillType(skill.getSkillType());
                createCommand.setTriggerConditionJson(skill.getTriggerConditionJson());
                createCommand.setContentJson(skill.getContentJson());
                createCommand.setSourceTaskId(taskId);
                createCommand.setTagsJson(skill.getTagsJson());
                createCommand.setCreatedByUserId(userId);

                skillService.createSkill(createCommand);
            }

        } catch (Exception e) {
            log.error("Failed to analyze and extract skills from task: {}", taskId, e);
        }

        return skills;
    }

    /**
     * 构建技能提取提示词
     */
    private String buildExtractionPrompt(String taskContext) {
        return """
                你是一个技能提取专家。请分析以下任务执行过程，提取可复用的技能。

                任务执行上下文：
                %s

                请识别任务中的可复用模式，并提取为技能。每个技能应包含：

                1. **技能类型**：
                   - workflow: 完整工作流程
                   - tool_chain: 工具调用链
                   - prompt_template: 提示词模板
                   - decision_rule: 决策规则

                2. **触发条件**：什么情况下应该使用这个技能

                3. **技能内容**：具体的执行步骤或模板

                4. **标签**：用于分类和检索的标签

                请以JSON格式返回技能列表：
                ```json
                {
                  "skills": [
                    {
                      "skillCode": "唯一标识符",
                      "skillName": "技能名称",
                      "description": "技能描述",
                      "skillType": "workflow/tool_chain/prompt_template/decision_rule",
                      "triggerCondition": {
                        "taskType": "任务类型",
                        "keywords": ["关键词"],
                        "conditions": "触发条件描述"
                      },
                      "content": {
                        "steps": ["步骤1", "步骤2"],
                        "template": "提示词模板",
                        "tools": ["工具1", "工具2"]
                      },
                      "tags": ["标签1", "标签2"]
                    }
                  ]
                }
                ```

                要求：
                - 只提取真正可复用的模式
                - 技能应该是通用的，不依赖特定数据
                - 触发条件应该清晰明确
                - 技能内容应该完整可执行

                只返回JSON，不要其他内容。
                """.formatted(taskContext);
    }

    /**
     * 解析模型返回的技能列表
     */
    private List<Skill> parseExtractedSkills(Long tenantId, Long taskId, Long userId, String analysisResult) {
        List<Skill> skills = new ArrayList<>();

        try {
            // 提取 JSON
            String json = extractJson(analysisResult);
            JsonNode root = objectMapper.readTree(json);
            JsonNode skillsNode = root.get("skills");

            if (skillsNode == null || !skillsNode.isArray()) {
                log.warn("No skills found in analysis result");
                return skills;
            }

            for (JsonNode skillNode : skillsNode) {
                Skill skill = new Skill();
                skill.setTenantId(tenantId);
                skill.setSkillCode(skillNode.get("skillCode").asText());
                skill.setSkillName(skillNode.get("skillName").asText());
                skill.setDescription(skillNode.get("description").asText());
                skill.setSkillType(skillNode.get("skillType").asText());

                // 触发条件
                JsonNode triggerNode = skillNode.get("triggerCondition");
                if (triggerNode != null) {
                    skill.setTriggerConditionJson(objectMapper.writeValueAsString(triggerNode));
                }

                // 技能内容
                JsonNode contentNode = skillNode.get("content");
                if (contentNode != null) {
                    skill.setContentJson(objectMapper.writeValueAsString(contentNode));
                }

                // 标签
                JsonNode tagsNode = skillNode.get("tags");
                if (tagsNode != null) {
                    skill.setTagsJson(objectMapper.writeValueAsString(tagsNode));
                }

                skill.setSourceTaskId(taskId);
                skill.setCreatedByUserId(userId);

                skills.add(skill);
            }

        } catch (Exception e) {
            log.error("Failed to parse extracted skills", e);
        }

        return skills;
    }

    /**
     * 从文本中提取 JSON
     */
    private String extractJson(String text) {
        if (!StringUtils.hasText(text)) {
            return "{}";
        }

        // 查找 JSON 块
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        return text;
    }
}
