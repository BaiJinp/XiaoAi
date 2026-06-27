package com.xiaoai.agent.skill.hub.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.skill.entity.Skill;
import com.xiaoai.agent.skill.hub.SkillHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技能市场控制器
 */
@RestController
@RequestMapping("/api/v1/skill-hub")
@CrossOrigin(origins = "*")
public class SkillHubController {

    private final SkillHubService skillHubService;

    @Autowired
    public SkillHubController(SkillHubService skillHubService) {
        this.skillHubService = skillHubService;
    }

    /**
     * 导出技能为 agentskills.io 标准格式
     */
    @GetMapping("/skills/{skillId}/export")
    public ApiResponse<String> exportSkill(@PathVariable Long skillId) {
        try {
            String standardJson = skillHubService.exportSkillAsStandard(skillId);
            return ApiResponse.success(standardJson);
        } catch (Exception e) {
            return ApiResponse.error("Failed to export skill: " + e.getMessage());
        }
    }

    /**
     * 从 agentskills.io 标准格式导入技能
     */
    @PostMapping("/skills/import")
    public ApiResponse<Skill> importSkill(
            @RequestParam Long tenantId,
            @RequestParam Long userId,
            @RequestBody String standardJson) {
        try {
            Skill skill = skillHubService.importSkillFromStandard(tenantId, standardJson, userId);
            return ApiResponse.success(skill);
        } catch (Exception e) {
            return ApiResponse.error("Failed to import skill: " + e.getMessage());
        }
    }

    /**
     * 分享技能
     */
    @PostMapping("/skills/{skillId}/share")
    public ApiResponse<SkillHubService.SkillShareInfo> shareSkill(
            @PathVariable Long skillId,
            @RequestParam(defaultValue = "link") String shareType) {
        try {
            SkillHubService.SkillShareInfo info = skillHubService.shareSkill(skillId, shareType);
            return ApiResponse.success(info);
        } catch (Exception e) {
            return ApiResponse.error("Failed to share skill: " + e.getMessage());
        }
    }

    /**
     * 通过分享代码获取技能
     */
    @GetMapping("/skills/share/{shareCode}")
    public ApiResponse<Skill> getSkillByShareCode(@PathVariable String shareCode) {
        Skill skill = skillHubService.getSkillByShareCode(shareCode);
        if (skill == null) {
            return ApiResponse.error("Skill not found");
        }
        return ApiResponse.success(skill);
    }

    /**
     * 搜索公开技能
     */
    @GetMapping("/skills/search")
    public ApiResponse<List<Skill>> searchPublicSkills(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(skillHubService.searchPublicSkills(keyword, limit));
    }

    /**
     * 获取热门技能
     */
    @GetMapping("/skills/trending")
    public ApiResponse<List<Skill>> getTrendingSkills(
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(skillHubService.getTrendingSkills(limit));
    }

    /**
     * 推荐技能
     */
    @GetMapping("/skills/recommend")
    public ApiResponse<List<Skill>> recommendSkills(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(skillHubService.recommendSkills(userId, limit));
    }

    /**
     * 下载技能标准格式文件
     */
    @GetMapping("/skills/{skillId}/download")
    public ApiResponse<Map<String, Object>> downloadSkill(@PathVariable Long skillId) {
        try {
            String standardJson = skillHubService.exportSkillAsStandard(skillId);
            // 获取技能信息
            String filename = "skill_" + skillId + ".skill.json";

            return ApiResponse.success(Map.of(
                    "filename", filename,
                    "content", standardJson,
                    "contentType", "application/json"
            ));
        } catch (Exception e) {
            return ApiResponse.error("Failed to download skill: " + e.getMessage());
        }
    }
}
