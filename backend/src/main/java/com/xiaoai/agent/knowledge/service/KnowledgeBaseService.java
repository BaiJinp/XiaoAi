package com.xiaoai.agent.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.knowledge.entity.KnowledgeBase;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    KnowledgeBase getKnowledgeBase(Long knowledgeBaseId);
}
