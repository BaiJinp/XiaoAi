package com.xiaoai.agent.agent.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMainAgentCommand {

    @NotNull
    private Long ownerUserId;
}
