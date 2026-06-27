package com.xiaoai.agent.skill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 技能实体
 * 存储从成功任务中自动提取的可复用技能
 */
@Getter
@Setter
@TableName("skill")
public class Skill extends TenantEntity {

    /**
     * 技能代码（唯一标识）
     */
    private String skillCode;

    /**
     * 技能名称
     */
    private String skillName;

    /**
     * 技能描述
     */
    private String description;

    /**
     * 技能类型（workflow、tool_chain、prompt_template、decision_rule）
     */
    private String skillType;

    /**
     * 触发条件（JSON：什么情况下使用这个技能）
     */
    private String triggerConditionJson;

    /**
     * 技能内容（JSON：具体的执行步骤或模板）
     */
    private String contentJson;

    /**
     * 来源任务ID（从哪个任务提取的）
     */
    private Long sourceTaskId;

    /**
     * 使用次数
     */
    private Integer usageCount;

    /**
     * 成功次数
     */
    private Integer successCount;

    /**
     * 成功率（0-100）
     */
    private Integer successRate;

    /**
     * 版本（技能改进时递增）
     */
    private Integer version;

    /**
     * 状态（active、deprecated、archived）
     */
    private String status;

    /**
     * 标签（JSON数组：用于分类和检索）
     */
    private String tagsJson;

    /**
     * 创建者用户ID
     */
    private Long createdByUserId;

    /**
     * 最后使用时间
     */
    private java.time.OffsetDateTime lastUsedAt;
}
