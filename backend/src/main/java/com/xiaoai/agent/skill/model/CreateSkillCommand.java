package com.xiaoai.agent.skill.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建技能命令
 */
@Getter
@Setter
public class CreateSkillCommand {

    private Long tenantId;

    private String skillCode;

    private String skillName;

    private String description;

    /**
     * 技能类型：workflow、tool_chain、prompt_template、decision_rule
     */
    private String skillType;

    /**
     * 触发条件 JSON
     */
    private String triggerConditionJson;

    /**
     * 技能内容 JSON
     */
    private String contentJson;

    /**
     * 来源任务ID
     */
    private Long sourceTaskId;

    /**
     * 标签 JSON 数组
     */
    private String tagsJson;

    /**
     * 创建者用户ID
     */
    private Long createdByUserId;
}
