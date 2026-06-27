package com.xiaoai.agent.collaboration.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartCollaborationSessionCommand {

    @NotNull
    private Long planId;
}
