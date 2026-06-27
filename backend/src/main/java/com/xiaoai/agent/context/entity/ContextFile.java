package com.xiaoai.agent.context.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 上下文文件实体
 * 存储项目级上下文配置（类似 AGENTS.md）
 */
@Getter
@Setter
@TableName("context_file")
public class ContextFile extends TenantEntity {

    /**
     * 文件代码（唯一标识）
     */
    private String fileCode;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件描述
     */
    private String description;

    /**
     * 文件内容（Markdown 格式）
     */
    private String content;

    /**
     * 文件类型（project, agent, workspace, custom）
     */
    private String fileType;

    /**
     * 关联的项目ID
     */
    private Long projectId;

    /**
     * 关联的 Agent ID
     */
    private Long agentId;

    /**
     * 创建者用户ID
     */
    private Long createdByUserId;

    /**
     * 是否自动加载到对话
     */
    private Boolean autoLoad;

    /**
     * 优先级（数字越大优先级越高）
     */
    private Integer priority;

    /**
     * 标签（JSON 数组）
     */
    private String tagsJson;

    /**
     * 状态（active, archived）
     */
    private String status;
}
