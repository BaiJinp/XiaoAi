package com.xiaoai.agent.task.model;

import com.xiaoai.agent.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskEventQuery extends PageQuery {

    private String eventType;

    private Long taskId;

    private Long runId;

    private Long agentVersionId;

    private String keyword;

    private Integer limit;
}
