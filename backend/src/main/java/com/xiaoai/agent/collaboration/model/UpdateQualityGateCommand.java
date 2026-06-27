package com.xiaoai.agent.collaboration.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateQualityGateCommand {

    private String resultJson;

    private String failReason;
}
