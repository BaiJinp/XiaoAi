package com.xiaoai.agent.personality.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.personality.entity.Personality;

import java.util.List;

/**
 * 人格服务接口
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface PersonalityService extends IService<Personality> {

    /**
     * 创建人格
     */
    Personality createPersonality(Long tenantId, String personalityCode, String personalityName,
                                  String description, String content, String personalityType,
                                  Long agentId, Long userId);

    /**
     * 根据代码获取人格
     */
    Personality getByCode(Long tenantId, String personalityCode);

    /**
     * 获取 Agent 的当前人格
     */
    Personality getAgentPersonality(Long tenantId, Long agentId);

    /**
     * 设置 Agent 的人格
     */
    void setAgentPersonality(Long tenantId, Long agentId, String personalityCode);

    /**
     * 搜索人格
     */
    List<Personality> searchPersonalities(Long tenantId, String keyword, String personalityType,
                                          Boolean isPublic, int limit);

    /**
     * 获取热门人格
     */
    List<Personality> getTopPersonalities(Long tenantId, int limit);

    /**
     * 增加使用次数
     */
    void incrementUsage(Long personalityId);

    /**
     * 从 Markdown 文件导入人格
     */
    Personality importFromMarkdown(Long tenantId, String markdown, Long userId);

    /**
     * 导出人格为 Markdown
     */
    String exportToMarkdown(Long personalityId);

    /**
     * 分享人格（设为公开）
     */
    void sharePersonality(Long personalityId);

    /**
     * 取消分享（设为私有）
     */
    void unsharePersonality(Long personalityId);
}
