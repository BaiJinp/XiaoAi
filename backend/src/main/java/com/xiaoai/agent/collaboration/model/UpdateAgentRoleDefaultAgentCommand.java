package com.xiaoai.agent.collaboration.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAgentRoleDefaultAgentCommand {

    private Long defaultAgentId;

    private Long defaultAgentVersionId;
}
