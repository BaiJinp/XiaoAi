package com.xiaoai.agent.common.context;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserContext {

    private final Long tenantId;

    private final Long userId;

    private final String username;

    private final String traceId;
}
