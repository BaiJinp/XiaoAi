package com.xiaoai.agent.memory.model;

import com.xiaoai.agent.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentMemoryPageQuery extends PageQuery {

    private Long agentId;

    private Long taskId;

    private Long sessionId;

    private Long userId;

    private String status;
}
