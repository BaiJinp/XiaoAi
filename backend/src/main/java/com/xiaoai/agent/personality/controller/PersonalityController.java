package com.xiaoai.agent.personality.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.personality.entity.Personality;
import com.xiaoai.agent.personality.service.PersonalityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 人格控制器
 */
@RestController
@RequestMapping("/api/v1/personalities")
@CrossOrigin(origins = "*")
public class PersonalityController {

    private final PersonalityService personalityService;

    @Autowired
    public PersonalityController(PersonalityService personalityService) {
        this.personalityService = personalityService;
    }

    /**
     * 创建人格
     */
    @PostMapping
    public ApiResponse<Personality> createPersonality(@RequestBody Map<String, Object> request) {
        Long tenantId = Long.valueOf(request.get("tenantId").toString());
        String code = (String) request.get("personalityCode");
        String name = (String) request.get("personalityName");
        String description = (String) request.get("description");
        String content = (String) request.get("content");
        String type = (String) request.get("personalityType");
        Long agentId = request.get("agentId") != null ? Long.valueOf(request.get("agentId").toString()) : null;
        Long userId = Long.valueOf(request.get("userId").toString());

        Personality personality = personalityService.createPersonality(
                tenantId, code, name, description, content, type, agentId, userId);
        return ApiResponse.success(personality);
    }

    /**
     * 根据代码获取人格
     */
    @GetMapping("/{code}")
    public ApiResponse<Personality> getByCode(@RequestParam Long tenantId, @PathVariable String code) {
        Personality personality = personalityService.getByCode(tenantId, code);
        if (personality == null) {
            return ApiResponse.error("Personality not found");
        }
        return ApiResponse.success(personality);
    }

    /**
     * 获取 Agent 的当前人格
     */
    @GetMapping("/agent/{agentId}")
    public ApiResponse<Personality> getAgentPersonality(@RequestParam Long tenantId, @PathVariable Long agentId) {
        Personality personality = personalityService.getAgentPersonality(tenantId, agentId);
        return ApiResponse.success(personality);
    }

    /**
     * 设置 Agent 的人格
     */
    @PostMapping("/agent/{agentId}/set")
    public ApiResponse<String> setAgentPersonality(@RequestParam Long tenantId,
                                                    @PathVariable Long agentId,
                                                    @RequestParam String personalityCode) {
        personalityService.setAgentPersonality(tenantId, agentId, personalityCode);
        return ApiResponse.success("Personality set successfully");
    }

    /**
     * 搜索人格
     */
    @GetMapping("/search")
    public ApiResponse<List<Personality>> searchPersonalities(@RequestParam Long tenantId,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String personalityType,
                                                               @RequestParam(required = false) Boolean isPublic,
                                                               @RequestParam(defaultValue = "20") int limit) {
        List<Personality> personalities = personalityService.searchPersonalities(
                tenantId, keyword, personalityType, isPublic, limit);
        return ApiResponse.success(personalities);
    }

    /**
     * 获取热门人格
     */
    @GetMapping("/top")
    public ApiResponse<List<Personality>> getTopPersonalities(@RequestParam Long tenantId,
                                                               @RequestParam(defaultValue = "10") int limit) {
        List<Personality> personalities = personalityService.getTopPersonalities(tenantId, limit);
        return ApiResponse.success(personalities);
    }

    /**
     * 从 Markdown 导入人格
     */
    @PostMapping("/import")
    public ApiResponse<Personality> importFromMarkdown(@RequestParam Long tenantId,
                                                        @RequestParam Long userId,
                                                        @RequestBody String markdown) {
        Personality personality = personalityService.importFromMarkdown(tenantId, markdown, userId);
        return ApiResponse.success(personality);
    }

    /**
     * 导出人格为 Markdown
     */
    @GetMapping("/{id}/export")
    public ApiResponse<String> exportToMarkdown(@PathVariable Long id) {
        String markdown = personalityService.exportToMarkdown(id);
        return ApiResponse.success(markdown);
    }

    /**
     * 分享人格
     */
    @PostMapping("/{id}/share")
    public ApiResponse<String> sharePersonality(@PathVariable Long id) {
        personalityService.sharePersonality(id);
        return ApiResponse.success("Personality shared successfully");
    }

    /**
     * 取消分享
     */
    @PostMapping("/{id}/unshare")
    public ApiResponse<String> unsharePersonality(@PathVariable Long id) {
        personalityService.unsharePersonality(id);
        return ApiResponse.success("Personality unshared successfully");
    }
}
