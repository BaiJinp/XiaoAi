package com.xiaoai.agent.runtime.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RunResumeCommand {

    private final Long tenantId;

    private final Long taskId;

    private final Long runId;

    private final String resumePayloadJson;
}
