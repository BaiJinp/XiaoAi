package com.xiaoai.agent.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.knowledge.entity.KnowledgeChunk;

public interface KnowledgeChunkService extends IService<KnowledgeChunk> {

    KnowledgeChunk getChunk(Long chunkId);
}
