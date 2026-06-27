package com.xiaoai.agent.skill.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 搜索技能查询
 */
@Getter
@Setter
public class SearchSkillQuery {

    private Long tenantId;

    /**
     * 关键词搜索（名称、描述、标签）
     */
    private String keyword;

    /**
     * 技能类型过滤
     */
    private String skillType;

    /**
     * 标签过滤
     */
    private String tag;

    /**
     * 最小成功率
     */
    private Integer minSuccessRate;

    /**
     * 状态过滤
     */
    private String status;

    /**
     * 返回数量限制
     */
    private Integer limit = 20;
}
