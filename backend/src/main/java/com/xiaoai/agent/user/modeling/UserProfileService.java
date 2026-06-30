package com.xiaoai.agent.user.modeling;

import com.baomidou.mybatisplus.extension.service.IService;

public interface UserProfileService extends IService<UserProfile> {

    default void updateProfile(UserProfile profile) {
        updateById(profile);
    }

    /**
     * 鑾峰彇鎴栧垱寤虹敤鎴风敾鍍?     */
    UserProfile getOrCreateProfile(Long tenantId, Long userId, Long agentId);

    /**
     * 鏇存柊鐢ㄦ埛鍋忓ソ
     */
    void updatePreferences(Long tenantId, Long userId, Long agentId, String preferencesJson);

    /**
     * 鏇存柊琛屼负妯″紡
     */
    void updateBehaviorPatterns(Long tenantId, Long userId, Long agentId, String behaviorPatternsJson);

    /**
     * 鏇存柊浜や簰椋庢牸
     */
    void updateInteractionStyle(Long tenantId, Long userId, Long agentId, String interactionStyleJson);

    /**
     * 璁板綍浜や簰
     */
    void recordInteraction(Long tenantId, Long userId, Long agentId);

    /**
     * 浠庡璇濅腑瀛︿範鐢ㄦ埛鐢诲儚
     */
    void learnFromConversation(Long tenantId, Long userId, Long agentId, String conversationText);
}
