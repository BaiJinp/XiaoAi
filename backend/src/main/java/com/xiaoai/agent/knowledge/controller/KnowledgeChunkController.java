package com.xiaoai.agent.knowledge.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.knowledge.entity.KnowledgeChunk;
import com.xiaoai.agent.knowledge.service.KnowledgeChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/knowledge-chunks")
public class KnowledgeChunkController {

    private final KnowledgeChunkService knowledgeChunkService;

    @GetMapping("/{id}")
public ApiResponse<KnowledgeChunk> getById(@PathVariable Long id) {
        return ApiResponse.success(knowledgeChunkService.getChunk(id));
    }
}
