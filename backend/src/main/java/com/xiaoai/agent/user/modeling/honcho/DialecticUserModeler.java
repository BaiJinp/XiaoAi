package com.xiaoai.agent.user.modeling.honcho;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.user.modeling.UserProfile;
import com.xiaoai.agent.user.modeling.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Honcho 辩证用户建模器
 * 通过分析对话构建深度用户画像
 *
 * 参考：https://github.com/plastic-labs/honcho
 */
@Component
public class DialecticUserModeler {

    private static final Logger log = LoggerFactory.getLogger(DialecticUserModeler.class);

    private final UserProfileService userProfileService;
    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;

    @Autowired
    public DialecticUserModeler(UserProfileService userProfileService,
                                ModelGateway modelGateway,
                                ObjectMapper objectMapper) {
        this.userProfileService = userProfileService;
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
    }

    /**
     * 分析对话并更新用户画像
     */
    public void analyzeConversation(Long tenantId, Long userId, Long agentId, String conversationText) {
        log.info("Analyzing conversation for user modeling: userId={}, agentId={}", userId, agentId);

        try {
            // 1. 获取当前用户画像
            UserProfile profile = userProfileService.getOrCreateProfile(tenantId, userId, agentId);

            // 2. 调用模型分析对话
            String analysisResult = callModelForAnalysis(conversationText, profile);

            // 3. 解析分析结果
            UserAnalysis analysis = parseAnalysisResult(analysisResult);

            // 4. 辩证更新用户画像
            dialecticUpdateProfile(profile, analysis);

            // 5. 保存更新后的画像
            userProfileService.updateProfile(profile);

            log.info("User modeling completed: userId={}, traits={}",
                    userId, analysis.getTraits().size());

        } catch (Exception e) {
            log.error("Failed to analyze conversation for user modeling", e);
        }
    }

    /**
     * 调用模型分析对话
     */
    private String callModelForAnalysis(String conversationText, UserProfile currentProfile) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请分析以下对话，提取用户的特征和偏好。\n\n");

        // 添加当前画像作为上下文
        if (currentProfile != null && currentProfile.getPreferencesJson() != null) {
            prompt.append("当前用户画像：\n");
            prompt.append(currentProfile.getPreferencesJson()).append("\n\n");
        }

        prompt.append("对话内容：\n");
        prompt.append(conversationText).append("\n\n");

        prompt.append("请分析并返回以下信息（JSON格式）：\n");
        prompt.append("{\n");
        prompt.append("  \"traits\": [\n");
        prompt.append("    {\"category\": \"性格特征\", \"trait\": \"具体特征\", \"confidence\": 0.8}\n");
        prompt.append("  ],\n");
        prompt.append("  \"preferences\": {\n");
        prompt.append("    \"communication_style\": \"沟通风格\",\n");
        prompt.append("    \"decision_making\": \"决策方式\",\n");
        prompt.append("    \"learning_style\": \"学习风格\"\n");
        prompt.append("  },\n");
        prompt.append("  \"interests\": [\"兴趣1\", \"兴趣2\"],\n");
        prompt.append("  \"expertise\": {\n");
        prompt.append("    \"domain\": \"专业领域\",\n");
        prompt.append("    \"level\": \"专家/中级/入门\"\n");
        prompt.append("  },\n");
        prompt.append("  \"behavioral_patterns\": [\n");
        prompt.append("    {\"pattern\": \"行为模式\", \"frequency\": \"频繁/偶尔/罕见\"}\n");
        prompt.append("  ],\n");
        prompt.append("  \"contradictions\": [\n");
        prompt.append("    {\"aspect\": \"方面\", \"old_belief\": \"旧观点\", \"new_belief\": \"新观点\"}\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");

        prompt.append("注意：\n");
        prompt.append("- 只返回JSON，不要其他内容\n");
        prompt.append("- 如果对话中没有相关信息，对应字段留空\n");
        prompt.append("- confidence 范围 0-1\n");

        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L); // 使用默认模型
        command.setPrompt(prompt.toString());

        ChatModelResponse response = modelGateway.chat(command);
        return response.getContent();
    }

    /**
     * 解析分析结果
     */
    private UserAnalysis parseAnalysisResult(String analysisResult) {
        try {
            String json = extractJson(analysisResult);
            JsonNode root = objectMapper.readTree(json);

            UserAnalysis analysis = new UserAnalysis();

            // 解析特征
            if (root.has("traits") && root.get("traits").isArray()) {
                for (JsonNode traitNode : root.get("traits")) {
                    UserTrait trait = new UserTrait();
                    trait.setCategory(traitNode.get("category").asText());
                    trait.setTrait(traitNode.get("trait").asText());
                    trait.setConfidence(traitNode.get("confidence").asDouble(0.5));
                    analysis.getTraits().add(trait);
                }
            }

            // 解析偏好
            if (root.has("preferences") && root.get("preferences").isObject()) {
                analysis.setPreferences(objectMapper.writeValueAsString(root.get("preferences")));
            }

            // 解析兴趣
            if (root.has("interests") && root.get("interests").isArray()) {
                analysis.setInterests(objectMapper.writeValueAsString(root.get("interests")));
            }

            // 解析专业领域
            if (root.has("expertise") && root.get("expertise").isObject()) {
                analysis.setExpertise(objectMapper.writeValueAsString(root.get("expertise")));
            }

            // 解析行为模式
            if (root.has("behavioral_patterns") && root.get("behavioral_patterns").isArray()) {
                analysis.setBehavioralPatterns(objectMapper.writeValueAsString(root.get("behavioral_patterns")));
            }

            // 解析矛盾点（辩证更新的关键）
            if (root.has("contradictions") && root.get("contradictions").isArray()) {
                for (JsonNode contradictionNode : root.get("contradictions")) {
                    Contradiction contradiction = new Contradiction();
                    contradiction.setAspect(contradictionNode.get("aspect").asText());
                    contradiction.setOldBelief(contradictionNode.get("old_belief").asText());
                    contradiction.setNewBelief(contradictionNode.get("new_belief").asText());
                    analysis.getContradictions().add(contradiction);
                }
            }

            return analysis;

        } catch (Exception e) {
            log.error("Failed to parse analysis result", e);
            return new UserAnalysis();
        }
    }

    /**
     * 辩证更新用户画像
     * 核心思想：不是简单覆盖，而是辩证地整合新旧信息
     */
    private void dialecticUpdateProfile(UserProfile profile, UserAnalysis analysis) {
        // 1. 更新特征（保留高置信度特征）
        updateTraits(profile, analysis);

        // 2. 更新偏好（合并新旧偏好）
        updatePreferences(profile, analysis);

        // 3. 更新兴趣（添加新兴趣）
        updateInterests(profile, analysis);

        // 4. 更新专业领域
        updateExpertise(profile, analysis);

        // 5. 更新行为模式
        updateBehavioralPatterns(profile, analysis);

        // 6. 处理矛盾点（辩证核心）
        handleContradictions(profile, analysis);

        // 7. 更新版本
        profile.setVersion(profile.getVersion() + 1);
    }

    /**
     * 更新用户特征
     */
    private void updateTraits(UserProfile profile, UserAnalysis analysis) {
        // TODO: 实现特征更新逻辑
        // 保留高置信度特征，更新低置信度特征
    }

    /**
     * 更新用户偏好
     */
    private void updatePreferences(UserProfile profile, UserAnalysis analysis) {
        if (analysis.getPreferences() != null) {
            // 合并新旧偏好
            String merged = mergeJsonObjects(profile.getPreferencesJson(), analysis.getPreferences());
            profile.setPreferencesJson(merged);
        }
    }

    /**
     * 更新用户兴趣
     */
    private void updateInterests(UserProfile profile, UserAnalysis analysis) {
        if (analysis.getInterests() != null) {
            // 添加新兴趣，去重
            String merged = mergeJsonArrays(profile.getInterests(), analysis.getInterests());
            profile.setInterests(merged);
        }
    }

    /**
     * 更新专业领域
     */
    private void updateExpertise(UserProfile profile, UserAnalysis analysis) {
        if (analysis.getExpertise() != null) {
            profile.setExpertise(analysis.getExpertise());
        }
    }

    /**
     * 更新行为模式
     */
    private void updateBehavioralPatterns(UserProfile profile, UserAnalysis analysis) {
        if (analysis.getBehavioralPatterns() != null) {
            String merged = mergeJsonArrays(profile.getBehaviorPatternsJson(), analysis.getBehavioralPatterns());
            profile.setBehaviorPatternsJson(merged);
        }
    }

    /**
     * 处理矛盾点（辩证核心）
     */
    private void handleContradictions(UserProfile profile, UserAnalysis analysis) {
        if (analysis.getContradictions().isEmpty()) {
            return;
        }

        log.info("Handling {} contradictions for user", analysis.getContradictions().size());

        // TODO: 实现矛盾处理逻辑
        // 1. 记录矛盾点
        // 2. 在后续对话中验证
        // 3. 逐步更新用户画像
    }

    /**
     * 合并 JSON 对象
     */
    private String mergeJsonObjects(String oldJson, String newJson) {
        if (oldJson == null || oldJson.isEmpty()) {
            return newJson;
        }
        if (newJson == null || newJson.isEmpty()) {
            return oldJson;
        }

        try {
            ObjectNode oldObj = (ObjectNode) objectMapper.readTree(oldJson);
            JsonNode newObj = objectMapper.readTree(newJson);

            newObj.fields().forEachRemaining(entry ->
                    oldObj.set(entry.getKey(), entry.getValue()));

            return objectMapper.writeValueAsString(oldObj);
        } catch (Exception e) {
            log.error("Failed to merge JSON objects", e);
            return newJson;
        }
    }

    /**
     * 合并 JSON 数组
     */
    private String mergeJsonArrays(String oldJson, String newJson) {
        if (oldJson == null || oldJson.isEmpty()) {
            return newJson;
        }
        if (newJson == null || newJson.isEmpty()) {
            return oldJson;
        }

        try {
            JsonNode oldArr = objectMapper.readTree(oldJson);
            JsonNode newArr = objectMapper.readTree(newJson);

            // 简单追加，实际应该去重
            var merged = objectMapper.createArrayNode();
            oldArr.forEach(merged::add);
            newArr.forEach(merged::add);

            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            log.error("Failed to merge JSON arrays", e);
            return newJson;
        }
    }

    /**
     * 从文本中提取 JSON
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * 用户分析结果
     */
    public static class UserAnalysis {
        private java.util.List<UserTrait> traits = new java.util.ArrayList<>();
        private String preferences;
        private String interests;
        private String expertise;
        private String behavioralPatterns;
        private java.util.List<Contradiction> contradictions = new java.util.ArrayList<>();

        // Getters and Setters
        public java.util.List<UserTrait> getTraits() { return traits; }
public void setTraits(java.util.List<UserTrait> traits) { this.traits = traits; }
public String getPreferences() { return preferences; }
public void setPreferences(String preferences) { this.preferences = preferences; }
public String getInterests() { return interests; }
public void setInterests(String interests) { this.interests = interests; }
public String getExpertise() { return expertise; }
public void setExpertise(String expertise) { this.expertise = expertise; }
public String getBehavioralPatterns() { return behavioralPatterns; }
public void setBehavioralPatterns(String behavioralPatterns) { this.behavioralPatterns = behavioralPatterns; }
        public java.util.List<Contradiction> getContradictions() { return contradictions; }
public void setContradictions(java.util.List<Contradiction> contradictions) { this.contradictions = contradictions; }
    }

    /**
     * 用户特征
     */
    public static class UserTrait {
        private String category;
        private String trait;
        private double confidence;

        // Getters and Setters
public String getCategory() { return category; }
public void setCategory(String category) { this.category = category; }
public String getTrait() { return trait; }
public void setTrait(String trait) { this.trait = trait; }
public double getConfidence() { return confidence; }
public void setConfidence(double confidence) { this.confidence = confidence; }
    }

    /**
     * 矛盾点
     */
    public static class Contradiction {
        private String aspect;
        private String oldBelief;
        private String newBelief;

        // Getters and Setters
public String getAspect() { return aspect; }
public void setAspect(String aspect) { this.aspect = aspect; }
public String getOldBelief() { return oldBelief; }
public void setOldBelief(String oldBelief) { this.oldBelief = oldBelief; }
public String getNewBelief() { return newBelief; }
public void setNewBelief(String newBelief) { this.newBelief = newBelief; }
    }
}
