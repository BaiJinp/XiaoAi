package com.xiaoai.agent.user.modeling;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

@Service
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile>
        implements UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileServiceImpl.class);

    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;

    @Autowired
    public UserProfileServiceImpl(ModelGateway modelGateway, ObjectMapper objectMapper) {
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
    }

    @Override
public UserProfile getOrCreateProfile(Long tenantId, Long userId, Long agentId) {
        UserProfile profile = getOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getTenantId, tenantId)
                .eq(UserProfile::getUserId, userId)
                .eq(UserProfile::getAgentId, agentId));

        if (profile == null) {
            profile = new UserProfile();
            profile.setTenantId(tenantId);
            profile.setUserId(userId);
            profile.setAgentId(agentId);
            profile.setInteractionCount(0);
            profile.setVersion(1);
            profile.setStatus("active");
            profile.setCreatedAt(OffsetDateTime.now());
            profile.setUpdatedAt(OffsetDateTime.now());
            save(profile);
            log.info("Created new user profile: userId={}, agentId={}", userId, agentId);
        }

        return profile;
    }

    @Override
public void updatePreferences(Long tenantId, Long userId, Long agentId, String preferencesJson) {
        UserProfile profile = getOrCreateProfile(tenantId, userId, agentId);
        profile.setPreferencesJson(preferencesJson);
        profile.setUpdatedAt(OffsetDateTime.now());
        profile.setVersion(profile.getVersion() + 1);
        updateById(profile);
        log.info("Updated user preferences: userId={}, agentId={}", userId, agentId);
    }

    @Override
public void updateBehaviorPatterns(Long tenantId, Long userId, Long agentId, String behaviorPatternsJson) {
        UserProfile profile = getOrCreateProfile(tenantId, userId, agentId);
        profile.setBehaviorPatternsJson(behaviorPatternsJson);
        profile.setUpdatedAt(OffsetDateTime.now());
        profile.setVersion(profile.getVersion() + 1);
        updateById(profile);
        log.info("Updated behavior patterns: userId={}, agentId={}", userId, agentId);
    }

    @Override
public void updateInteractionStyle(Long tenantId, Long userId, Long agentId, String interactionStyleJson) {
        UserProfile profile = getOrCreateProfile(tenantId, userId, agentId);
        profile.setInteractionStyleJson(interactionStyleJson);
        profile.setUpdatedAt(OffsetDateTime.now());
        profile.setVersion(profile.getVersion() + 1);
        updateById(profile);
        log.info("Updated interaction style: userId={}, agentId={}", userId, agentId);
    }

    @Override
public void recordInteraction(Long tenantId, Long userId, Long agentId) {
        UserProfile profile = getOrCreateProfile(tenantId, userId, agentId);
        profile.setInteractionCount(profile.getInteractionCount() + 1);
        profile.setLastInteractionAt(OffsetDateTime.now());
        profile.setUpdatedAt(OffsetDateTime.now());
        updateById(profile);
    }

    @Override
public void learnFromConversation(Long tenantId, Long userId, Long agentId, String conversationText) {
        if (!StringUtils.hasText(conversationText)) {
            return;
        }

        try {
            // 调用模型分析对话，提取用户特征
            String prompt = buildLearningPrompt(conversationText);

            ChatModelCommand command = new ChatModelCommand();
            command.setModelId(1L); // 使用默认模型
            command.setPrompt(prompt);

            ChatModelResponse response = modelGateway.chat(command);
            String analysisResult = response.getContent();

            // 解析分析结果
            parseAndUpdateProfile(tenantId, userId, agentId, analysisResult);

            log.info("Learned from conversation: userId={}, agentId={}", userId, agentId);

        } catch (Exception e) {
            log.error("Failed to learn from conversation", e);
        }
    }

    private String buildLearningPrompt(String conversationText) {
        return String.format("""
                请分析以下对话，提取用户的偏好、行为模式和交互风格特征。

                对话内容：
                %s

                请以JSON格式返回分析结果，包含以下字段：
                {
                  "preferences": {
                    "language": "用户偏好的语言",
                    "response_style": "用户偏好的回复风格（concise/detailed/technical/casual）",
                    "code_style": "用户偏好的代码风格"
                  },
                  "behavior_patterns": {
                    "task_types": ["用户常做的任务类型"],
                    "active_hours": "用户活跃时间段"
                  },
                  "interaction_style": {
                    "formality": "正式程度（formal/casual/mixed）",
                    "detail_level": "详细程度（high/medium/low）",
                    "humor": "幽默程度（high/medium/low/none）"
                  },
                  "skill_levels": {
                    "programming": "编程水平（beginner/intermediate/advanced）",
                    "domain_knowledge": "领域知识水平"
                  }
                }

                只返回JSON，不要其他内容。
                """, conversationText);
    }

    private void parseAndUpdateProfile(Long tenantId, Long userId, Long agentId, String analysisResult) {
        try {
            // 提取JSON
            String json = extractJson(analysisResult);
            JsonNode root = objectMapper.readTree(json);

            UserProfile profile = getOrCreateProfile(tenantId, userId, agentId);

            // 更新偏好
            if (root.has("preferences")) {
                profile.setPreferencesJson(objectMapper.writeValueAsString(root.get("preferences")));
            }

            // 更新行为模式
            if (root.has("behavior_patterns")) {
                profile.setBehaviorPatternsJson(objectMapper.writeValueAsString(root.get("behavior_patterns")));
            }

            // 更新交互风格
            if (root.has("interaction_style")) {
                profile.setInteractionStyleJson(objectMapper.writeValueAsString(root.get("interaction_style")));
            }

            // 更新技能水平
            if (root.has("skill_levels")) {
                profile.setSkillLevelsJson(objectMapper.writeValueAsString(root.get("skill_levels")));
            }

            profile.setUpdatedAt(OffsetDateTime.now());
            profile.setVersion(profile.getVersion() + 1);
            updateById(profile);

        } catch (Exception e) {
            log.error("Failed to parse and update profile", e);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
