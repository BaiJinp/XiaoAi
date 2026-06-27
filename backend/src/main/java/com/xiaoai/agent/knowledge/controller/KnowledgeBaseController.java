package com.xiaoai.agent.knowledge.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.knowledge.entity.KnowledgeBase;
import com.xiaoai.agent.knowledge.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping("/{id}")
public ApiResponse<KnowledgeBase> getById(@PathVariable Long id) {
        return ApiResponse.success(knowledgeBaseService.getKnowledgeBase(id));
    }
}
