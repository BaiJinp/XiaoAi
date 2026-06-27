package com.xiaoai.agent.skill.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.runtime.engine.ContextPackage;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.skill.entity.Skill;
import com.xiaoai.agent.skill.model.SearchSkillQuery;
import com.xiaoai.agent.skill.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能执行器
 * 在任务执行时匹配并应用相关技能，将技能内容注入到 Agent 执行上下文中。
 *
 * 支持四种技能类型：
 * - workflow: 工作流步骤序列，注入为推荐执行路径
 * - tool_chain: 工具调用链，注入为预编排的工具调用
 * - prompt_template: 提示词模板，注入到 system prompt 中
 * - decision_rule: 决策规则，注入到 reflect 阶段用于结果校验
 */
@Component
public class SkillExecutor {

    private static final Logger log = LoggerFactory.getLogger(SkillExecutor.class);
    private static final int MAX_MATCHED_SKILLS = 3;

    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SkillExecutor(SkillService skillService, ObjectMapper objectMapper) {
        this.skillService = skillService;
        this.objectMapper = objectMapper;
    }

    /**
     * 为任务匹配并应用技能
     *
     * @param command 运行启动命令
     * @param contextPackage 上下文包
     * @return 应用的技能列表
     */
    public List<Skill> applySkillsToTask(RunStartCommand command, ContextPackage contextPackage) {
        Long tenantId = command.getTenantId();
        String inputText = contextPackage.getInputText();

        log.info("Applying skills to task: tenantId={}, taskId={}", tenantId, command.getTaskId());

        // 1. 搜索匹配的技能
        List<Skill> matchedSkills = matchSkills(tenantId, inputText);

        if (matchedSkills.isEmpty()) {
            log.info("No matching skills found for task: taskId={}", command.getTaskId());
            return List.of();
        }

        log.info("Found {} matching skills for task: taskId={}", matchedSkills.size(), command.getTaskId());

        // 2. 应用技能到上下文
        for (Skill skill : matchedSkills) {
            applySkillToContext(skill, contextPackage);

            // 3. 记录技能使用
            skillService.recordUsage(skill.getId(), true);
        }

        return matchedSkills;
    }

    /**
     * 匹配与用户输入相关的技能（供 AgentLoop 调用）
     * <p>
     * 从输入文本中提取关键词，搜索租户下 active 状态的技能，
     * 按使用次数和成功率排序，返回 top N 匹配结果。
     * </p>
     *
     * @param tenantId  租户ID
     * @param inputText 用户输入文本
     * @return 匹配的技能列表（最多 MAX_MATCHED_SKILLS 条）
     */
    public List<Skill> matchSkills(Long tenantId, String inputText) {
        List<String> keywords = extractKeywords(inputText);
        if (keywords.isEmpty()) {
            return List.of();
        }

        List<Skill> allMatched = new ArrayList<>();
        for (String keyword : keywords) {
            SearchSkillQuery query = new SearchSkillQuery();
            query.setTenantId(tenantId);
            query.setKeyword(keyword);
            query.setLimit(MAX_MATCHED_SKILLS);
            List<Skill> skills = skillService.searchSkills(query);
            for (Skill skill : skills) {
                // 去重：避免同一个 skill 被多次加入
                if (allMatched.stream().noneMatch(s -> s.getId().equals(skill.getId()))) {
                    allMatched.add(skill);
                }
            }
            if (allMatched.size() >= MAX_MATCHED_SKILLS) {
                break;
            }
        }

        return allMatched.size() > MAX_MATCHED_SKILLS
                ? allMatched.subList(0, MAX_MATCHED_SKILLS)
                : allMatched;
    }

    /**
     * 构建技能上下文文本，用于注入到 plan prompt 中
     * <p>
     * 将匹配到的技能内容格式化为可读文本，包含技能名称、类型、描述和具体内容。
     * </p>
     *
     * @param matchedSkills 匹配到的技能列表
     * @return 格式化的技能上下文文本
     */
    public String buildSkillContext(List<Skill> matchedSkills) {
        if (matchedSkills == null || matchedSkills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 可复用技能\n\n");
        sb.append("以下技能是从历史成功任务中提取的可复用模式，请在制定执行计划时优先参考：\n\n");

        for (Skill skill : matchedSkills) {
            sb.append("### 技能: ").append(skill.getSkillName()).append("\n");
            sb.append("- 代码: ").append(skill.getSkillCode()).append("\n");
            sb.append("- 类型: ").append(skill.getSkillType()).append("\n");
            if (StringUtils.hasText(skill.getDescription())) {
                sb.append("- 描述: ").append(skill.getDescription()).append("\n");
            }
            sb.append("- 成功率: ").append(skill.getSuccessRate()).append("%\n");

            // 解析 contentJson 并输出具体内容
            try {
                JsonNode content = objectMapper.readTree(skill.getContentJson());
                sb.append("- 内容:\n");
                switch (skill.getSkillType()) {
                    case "workflow":
                        appendWorkflowContent(sb, content);
                        break;
                    case "tool_chain":
                        appendToolChainContent(sb, content);
                        break;
                    case "prompt_template":
                        appendPromptTemplateContent(sb, content);
                        break;
                    case "decision_rule":
                        appendDecisionRuleContent(sb, content);
                        break;
                    default:
                        sb.append("  ").append(skill.getContentJson()).append("\n");
                }
            } catch (Exception e) {
                sb.append("- 内容: ").append(skill.getContentJson()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 记录技能使用的反馈结果（供 AgentLoop reflect 后调用）
     *
     * @param matchedSkills 本次使用的技能列表
     * @param taskSuccess   任务是否成功
     */
    public void recordSkillFeedback(List<Skill> matchedSkills, boolean taskSuccess) {
        if (matchedSkills == null || matchedSkills.isEmpty()) {
            return;
        }
        for (Skill skill : matchedSkills) {
            try {
                skillService.recordUsage(skill.getId(), taskSuccess);
            } catch (Exception e) {
                log.error("Failed to record skill feedback: skillId={}, success={}", skill.getId(), taskSuccess, e);
            }
        }
    }

    // ========== 技能应用方法 ==========

    /**
     * 将技能应用到上下文
     */
    private void applySkillToContext(Skill skill, ContextPackage contextPackage) {
        try {
            JsonNode contentNode = objectMapper.readTree(skill.getContentJson());

            switch (skill.getSkillType()) {
                case "workflow":
                    applyWorkflowSkill(skill, contentNode, contextPackage);
                    break;
                case "tool_chain":
                    applyToolChainSkill(skill, contentNode, contextPackage);
                    break;
                case "prompt_template":
                    applyPromptTemplateSkill(skill, contentNode, contextPackage);
                    break;
                case "decision_rule":
                    applyDecisionRuleSkill(skill, contentNode, contextPackage);
                    break;
                default:
                    log.warn("Unknown skill type: {}", skill.getSkillType());
            }

            log.info("Skill applied: code={}, type={}, name={}",
                    skill.getSkillCode(), skill.getSkillType(), skill.getSkillName());

        } catch (Exception e) {
            log.error("Failed to apply skill: code={}", skill.getSkillCode(), e);
        }
    }

    /**
     * 应用工作流技能
     * <p>
     * 将工作流步骤解析为执行步骤序列，注入到上下文中作为推荐执行路径。
     * contentJson 格式: {"steps": [{"stepType": "...", "description": "...", ...}]}
     * </p>
     */
    private void applyWorkflowSkill(Skill skill, JsonNode contentNode, ContextPackage contextPackage) {
        JsonNode stepsNode = contentNode.get("steps");
        if (stepsNode != null && stepsNode.isArray()) {
            // 将工作流步骤构建为推荐执行路径文本，后续注入到 plan prompt
            StringBuilder sb = new StringBuilder();
            sb.append("[Workflow Skill: ").append(skill.getSkillName()).append("]\n");
            sb.append("推荐执行步骤:\n");
            for (int i = 0; i < stepsNode.size(); i++) {
                JsonNode step = stepsNode.get(i);
                sb.append("  ").append(i + 1).append(". ");
                sb.append(step.has("stepType") ? step.get("stepType").asText() : "unknown");
                sb.append(" - ");
                sb.append(step.has("description") ? step.get("description").asText() : "无描述");
                sb.append("\n");
            }
            log.debug("Workflow skill applied: code={}, steps={}", skill.getSkillCode(), stepsNode.size());
        }
    }

    /**
     * 应用工具链技能
     * <p>
     * 将工具链解析为预编排的工具调用序列，注入到上下文中。
     * contentJson 格式: {"tools": [{"toolCode": "...", "payload": {...}}, ...]}
     * </p>
     */
    private void applyToolChainSkill(Skill skill, JsonNode contentNode, ContextPackage contextPackage) {
        JsonNode toolsNode = contentNode.get("tools");
        if (toolsNode != null && toolsNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[ToolChain Skill: ").append(skill.getSkillName()).append("]\n");
            sb.append("推荐工具调用链:\n");
            for (int i = 0; i < toolsNode.size(); i++) {
                JsonNode tool = toolsNode.get(i);
                sb.append("  ").append(i + 1).append(". ");
                sb.append(tool.has("toolCode") ? tool.get("toolCode").asText() : "unknown");
                if (tool.has("description")) {
                    sb.append(" - ").append(tool.get("description").asText());
                }
                sb.append("\n");
            }
            log.debug("Tool chain skill applied: code={}, tools={}", skill.getSkillCode(), toolsNode.size());
        }
    }

    /**
     * 应用提示词模板技能
     * <p>
     * 将提示词模板作为 system prompt 的补充片段。
     * contentJson 格式: {"template": "提示词模板内容...", "variables": {"key": "value"}}
     * </p>
     */
    private void applyPromptTemplateSkill(Skill skill, JsonNode contentNode, ContextPackage contextPackage) {
        String template = contentNode.has("template") ? contentNode.get("template").asText() : null;
        if (StringUtils.hasText(template)) {
            // 替换模板变量
            JsonNode variables = contentNode.get("variables");
            String resolved = template;
            if (variables != null && variables.isObject()) {
                var fields = variables.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue().asText());
                }
            }
            log.debug("Prompt template skill applied: code={}, templateLength={}", skill.getSkillCode(), resolved.length());
        }
    }

    /**
     * 应用决策规则技能
     * <p>
     * 将决策规则解析为条件-动作规则，在 reflect 阶段用于结果校验。
     * contentJson 格式: {"rules": [{"condition": "...", "action": "...", "description": "..."}]}
     * </p>
     */
    private void applyDecisionRuleSkill(Skill skill, JsonNode contentNode, ContextPackage contextPackage) {
        JsonNode rulesNode = contentNode.get("rules");
        if (rulesNode != null && rulesNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[DecisionRule Skill: ").append(skill.getSkillName()).append("]\n");
            sb.append("决策规则:\n");
            for (int i = 0; i < rulesNode.size(); i++) {
                JsonNode rule = rulesNode.get(i);
                sb.append("  - IF ").append(rule.has("condition") ? rule.get("condition").asText() : "unknown");
                sb.append(" THEN ").append(rule.has("action") ? rule.get("action").asText() : "unknown");
                if (rule.has("description")) {
                    sb.append(" // ").append(rule.get("description").asText());
                }
                sb.append("\n");
            }
            log.debug("Decision rule skill applied: code={}, rules={}", skill.getSkillCode(), rulesNode.size());
        }
    }

    // ========== 内容格式化辅助方法 ==========

    private void appendWorkflowContent(StringBuilder sb, JsonNode content) {
        JsonNode steps = content.get("steps");
        if (steps != null && steps.isArray()) {
            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                sb.append("  步骤 ").append(i + 1).append(": ");
                sb.append(step.has("stepType") ? step.get("stepType").asText() : "unknown");
                sb.append(" - ").append(step.has("description") ? step.get("description").asText() : "").append("\n");
            }
        }
    }

    private void appendToolChainContent(StringBuilder sb, JsonNode content) {
        JsonNode tools = content.get("tools");
        if (tools != null && tools.isArray()) {
            for (int i = 0; i < tools.size(); i++) {
                JsonNode tool = tools.get(i);
                sb.append("  调用 ").append(i + 1).append(": ");
                sb.append(tool.has("toolCode") ? tool.get("toolCode").asText() : "unknown");
                if (tool.has("description")) {
                    sb.append(" - ").append(tool.get("description").asText());
                }
                sb.append("\n");
            }
        }
    }

    private void appendPromptTemplateContent(StringBuilder sb, JsonNode content) {
        String template = content.has("template") ? content.get("template").asText() : "";
        sb.append("  模板: ").append(template.length() > 200 ? template.substring(0, 200) + "..." : template).append("\n");
    }

    private void appendDecisionRuleContent(StringBuilder sb, JsonNode content) {
        JsonNode rules = content.get("rules");
        if (rules != null && rules.isArray()) {
            for (int i = 0; i < rules.size(); i++) {
                JsonNode rule = rules.get(i);
                sb.append("  IF ").append(rule.has("condition") ? rule.get("condition").asText() : "?");
                sb.append(" THEN ").append(rule.has("action") ? rule.get("action").asText() : "?");
                sb.append("\n");
            }
        }
    }

    // ========== 关键词提取 ==========

    /**
     * 搜索匹配的技能
     */
    private List<Skill> findMatchingSkills(Long tenantId, String inputText) {
        List<String> keywords = extractKeywords(inputText);

        if (keywords.isEmpty()) {
            return List.of();
        }

        SearchSkillQuery query = new SearchSkillQuery();
        query.setTenantId(tenantId);
        query.setLimit(10);

        for (String keyword : keywords) {
            query.setKeyword(keyword);
            List<Skill> skills = skillService.searchSkills(query);
            if (!skills.isEmpty()) {
                return skills;
            }
        }

        return List.of();
    }

    /**
     * 从输入文本中提取关键词
     */
    private List<String> extractKeywords(String inputText) {
        List<String> keywords = new ArrayList<>();

        if (!StringUtils.hasText(inputText)) {
            return keywords;
        }

        // 移除常见停用词
        String[] stopWords = {"的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
                "请", "帮", "给", "把", "用", "对", "从", "以", "及", "等", "来", "去", "做", "吗", "呢"};
        String cleaned = inputText;
        for (String stopWord : stopWords) {
            cleaned = cleaned.replace(stopWord, " ");
        }

        // 按空格和标点分词
        String[] words = cleaned.split("[\\s,，。！？；：、]+");
        for (String word : words) {
            word = word.trim();
            if (word.length() >= 2) {
                keywords.add(word);
            }
        }

        return keywords.size() > 5 ? keywords.subList(0, 5) : keywords;
    }
}
