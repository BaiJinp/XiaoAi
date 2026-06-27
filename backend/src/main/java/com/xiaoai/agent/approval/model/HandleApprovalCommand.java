package com.xiaoai.agent.approval.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HandleApprovalCommand {

    @NotNull
    private Long operatorUserId;

    @NotBlank
    private String action;

    private String commentText;

    private String actionPayloadJson;
}
