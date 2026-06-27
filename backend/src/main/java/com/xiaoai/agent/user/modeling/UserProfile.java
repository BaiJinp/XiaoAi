package com.xiaoai.agent.user.modeling;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户画像实体
 * 存储用户偏好、行为模式和个性化配置
 */
@Getter
@Setter
@TableName("user_profile")
public class UserProfile extends TenantEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * Agent ID（用户与特定 Agent 的画像）
     */
    private Long agentId;

    /**
     * 用户偏好（JSON）
     * 例如：{"language": "zh-CN", "response_style": "concise", "code_style": "python"}
     */
    private String preferencesJson;

    /**
     * 行为模式（JSON）
     * 例如：{"active_hours": "9-18", "task_types": ["code_review", "debugging"]}
     */
    private String behaviorPatternsJson;

    /**
     * 交互风格（JSON）
     * 例如：{"formality": "casual", "detail_level": "medium", "humor": "low"}
     */
    private String interactionStyleJson;

    /**
     * 技能水平（JSON）
     * 例如：{"programming": "advanced", "domain_knowledge": "intermediate"}
     */
    private String skillLevelsJson;

    /**
     * 常用工具（JSON 数组）
     * 例如：["git", "docker", "python"]
     */
    private String常用工具Json;

    /**
     * 对话历史摘要
     */
    private String conversationSummary;

    /**
     * 交互次数
     */
    private Integer interactionCount;

    /**
     * 最后交互时间
     */
    private java.time.OffsetDateTime lastInteractionAt;

    /**
     * 画像版本（每次更新递增）
     */
    private Integer version;

    /**
     * 状态（active、inactive）
     */
    private String status;
}
