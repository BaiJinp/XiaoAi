package com.xiaoai.agent.skill.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 改进技能命令
 */
@Getter
@Setter
public class ImproveSkillCommand {

    private Long skillId;

    private Long tenantId;

    /**
     * 改进内容 JSON
     */
    private String improvedContentJson;

    /**
     * 改进原因
     */
    private String improvementReason;

    /**
     * 是否自动改进（由系统触发）
     */
    private Boolean autoImproved = false;
}
