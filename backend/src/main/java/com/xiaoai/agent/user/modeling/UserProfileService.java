package com.xiaoai.agent.user.modeling;

import com.baomidou.mybatisplus.extension.service.IService;

public interface UserProfileService extends IService<UserProfile> {

    /**
     * 获取或创建用户画像
     */
    UserProfile getOrCreateProfile(Long tenantId, Long userId, Long agentId);

    /**
     * 更新用户偏好
     */
    void updatePreferences(Long tenantId, Long userId, Long agentId, String preferencesJson);

    /**
     * 更新行为模式
     */
    void updateBehaviorPatterns(Long tenantId, Long userId, Long agentId, String behaviorPatternsJson);

    /**
     * 更新交互风格
     */
    void updateInteractionStyle(Long tenantId, Long userId, Long agentId, String interactionStyleJson);

    /**
     * 记录交互
     */
    void recordInteraction(Long tenantId, Long userId, Long agentId);

    /**
     * 从对话中学习用户画像
     */
    void learnFromConversation(Long tenantId, Long userId, Long agentId, String conversationText);
}
