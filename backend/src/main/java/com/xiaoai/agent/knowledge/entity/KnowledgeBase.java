package com.xiaoai.agent.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("knowledge_base")
public class KnowledgeBase extends TenantEntity {
    private String kbCode;

    private String kbName;

    private String description;

    private String status;

    private String configJson;
}
