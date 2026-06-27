package com.xiaoai.agent.collaboration.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCollaborationRoleBindingCommand {

    private Long agentId;

    private Long agentVersionId;

    private String bindingScope;

    private String bindingKey;
}
