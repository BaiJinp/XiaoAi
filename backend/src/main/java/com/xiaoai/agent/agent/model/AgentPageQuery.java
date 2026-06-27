package com.xiaoai.agent.agent.model;

import com.xiaoai.agent.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentPageQuery extends PageQuery {

    private String agentName;

    private String status;
}
