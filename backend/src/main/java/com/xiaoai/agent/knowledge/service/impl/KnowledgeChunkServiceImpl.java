package com.xiaoai.agent.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.knowledge.entity.KnowledgeChunk;
import com.xiaoai.agent.knowledge.mapper.KnowledgeChunkMapper;
import com.xiaoai.agent.knowledge.service.KnowledgeChunkService;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeChunkServiceImpl extends ServiceImpl<KnowledgeChunkMapper, KnowledgeChunk> implements KnowledgeChunkService {

    @Override
public KnowledgeChunk getChunk(Long chunkId) {
        Long tenantId = UserContextHolder.requireTenantId();
        KnowledgeChunk chunk = getBaseMapper().selectOne(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getTenantId, tenantId)
                .eq(KnowledgeChunk::getId, chunkId)
                .last("limit 1"));
        if (chunk == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Knowledge chunk not found");
        }
        return chunk;
    }
}
