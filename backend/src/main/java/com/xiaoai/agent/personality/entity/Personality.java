package com.xiaoai.agent.personality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 人格实体
 * 存储 Agent 的人格配置（SOUL.md 风格）
 */
@Getter
@Setter
@TableName("personality")
public class Personality extends TenantEntity {

    /**
     * 人格代码（唯一标识）
     */
    private String personalityCode;

    /**
     * 人格名称
     */
    private String personalityName;

    /**
     * 人格描述
     */
    private String description;

    /**
     * 人格内容（Markdown 格式，类似 SOUL.md）
     */
    private String content;

    /**
     * 人格类型（professional, friendly, expert, creative, custom）
     */
    private String personalityType;

    /**
     * 适用的 Agent ID（空表示通用）
     */
    private Long agentId;

    /**
     * 创建者用户ID
     */
    private Long createdByUserId;

    /**
     * 是否公开（可分享）
     */
    private Boolean isPublic;

    /**
     * 使用次数
     */
    private Integer usageCount;

    /**
     * 评分（1-5）
     */
    private Integer rating;

    /**
     * 标签（JSON 数组）
     */
    private String tagsJson;

    /**
     * 状态（active, archived）
     */
    private String status;
}
