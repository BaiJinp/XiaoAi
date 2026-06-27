package com.xiaoai.agent.collaboration.model;

import com.xiaoai.agent.collaboration.entity.CollaborationTemplate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CollaborationTemplateResponse {

    private final Long templateId;

    private final String templateCode;

    private final String templateName;

    private final String domainCode;

    private final String strategyType;

    private final String templateJson;

    private final String status;

    public static CollaborationTemplateResponse from(CollaborationTemplate template) {
        return CollaborationTemplateResponse.builder()
                .templateId(template.getId())
                .templateCode(template.getTemplateCode())
                .templateName(template.getTemplateName())
                .domainCode(template.getDomainCode())
                .strategyType(template.getStrategyType())
                .templateJson(template.getTemplateJson())
                .status(template.getStatus())
                .build();
    }
}
