package com.xiaoai.agent.task.model;

import com.xiaoai.agent.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskPageQuery extends PageQuery {

    private Long agentId;

    private Long userId;

    private String status;
}
