package com.xiaoai.agent.runtime.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("runtime_node")
public class RuntimeNode extends TenantEntity {
    private String runtimeCode;

    private String runtimeType;

    private String runtimeName;

    private String endpointUrl;

    private String capabilityJson;

    private String status;

    private OffsetDateTime lastHeartbeatAt;
}
