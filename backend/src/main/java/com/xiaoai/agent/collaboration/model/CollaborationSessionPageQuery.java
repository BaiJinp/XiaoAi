package com.xiaoai.agent.collaboration.model;

import com.xiaoai.agent.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CollaborationSessionPageQuery extends PageQuery {

    private Long templateId;

    private String strategyType;

    private String status;
}
