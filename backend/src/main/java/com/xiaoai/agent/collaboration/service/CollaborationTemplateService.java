package com.xiaoai.agent.collaboration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.collaboration.entity.CollaborationTemplate;
import com.xiaoai.agent.collaboration.model.CollaborationTemplateResponse;

import java.util.List;

public interface CollaborationTemplateService extends IService<CollaborationTemplate> {

    CollaborationTemplate getTemplate(Long templateId);

    List<CollaborationTemplateResponse> listActiveTemplates(String domainCode);
}
