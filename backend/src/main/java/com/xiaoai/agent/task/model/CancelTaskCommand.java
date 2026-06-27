package com.xiaoai.agent.task.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelTaskCommand {

    private Long operatorUserId;

    private String reason;
}
