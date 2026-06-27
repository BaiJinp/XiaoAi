package com.xiaoai.agent.skill.hub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.skill.entity.Skill;
import com.xiaoai.agent.skill.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 技能市场服务
 * 支持技能分享、导入、导出，兼容 agentskills.io 标准
 */
@Service
public class SkillHubService {

    private static final Logger log = LoggerFactory.getLogger(SkillHubService.class);

    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SkillHubService(SkillService skillService, ObjectMapper objectMapper) {
        this.skillService = skillService;
        this.objectMapper = objectMapper;
    }

    /**
     * 导出技能为 agentskills.io 标准格式
     */
    public String exportSkillAsStandard(Long skillId) {
        Skill skill = skillService.getById(skillId);
        if (skill == null) {
            throw new IllegalArgumentException("Skill not found: " + skillId);
        }

        try {
            ObjectNode standard = objectMapper.createObjectNode();

            // 基本信息
            standard.put("name", skill.getSkillName());
            standard.put("description", skill.getDescription());
            standard.put("version", "1.0.0");
            standard.put("author", "Agent-xiaoAI");
            standard.put("license", "MIT");

            // 技能类型
            standard.put("type", skill.getSkillType());

            // 触发条件
            if (skill.getTriggerConditionJson() != null) {
                standard.set("triggers", objectMapper.readTree(skill.getTriggerConditionJson()));
            }

            // 技能内容
            if (skill.getContentJson() != null) {
                standard.set("content", objectMapper.readTree(skill.getContentJson()));
            }

            // 标签
            if (skill.getTagsJson() != null) {
                standard.set("tags", objectMapper.readTree(skill.getTagsJson()));
            }

            // 元数据
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("usageCount", skill.getUsageCount());
            metadata.put("successRate", skill.getSuccessRate());
            metadata.put("createdAt", skill.getCreatedAt().toString());
            standard.set("metadata", metadata);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(standard);

        } catch (Exception e) {
            log.error("Failed to export skill as standard: {}", skillId, e);
            throw new RuntimeException("Failed to export skill", e);
        }
    }

    /**
     * 从 agentskills.io 标准格式导入技能
     */
    public Skill importSkillFromStandard(Long tenantId, String standardJson, Long userId) {
        try {
            JsonNode standard = objectMapper.readTree(standardJson);

            // 创建技能
            Skill skill = new Skill();
            skill.setTenantId(tenantId);
            skill.setSkillCode("imported_" + System.currentTimeMillis());
            skill.setSkillName(standard.get("name").asText());
            skill.setDescription(standard.has("description") ? standard.get("description").asText() : "");
            skill.setSkillType(standard.has("type") ? standard.get("type").asText() : "workflow");

            // 触发条件
            if (standard.has("triggers")) {
                skill.setTriggerConditionJson(objectMapper.writeValueAsString(standard.get("triggers")));
            }

            // 技能内容
            if (standard.has("content")) {
                skill.setContentJson(objectMapper.writeValueAsString(standard.get("content")));
            }

            // 标签
            if (standard.has("tags")) {
                skill.setTagsJson(objectMapper.writeValueAsString(standard.get("tags")));
            }

            skill.setCreatedByUserId(userId);
            skill.setUsageCount(0);
            skill.setSuccessCount(0);
            skill.setSuccessRate(0);
            skill.setVersion(1);
            skill.setStatus("active");

            return skillService.createSkillFromEntity(skill);

        } catch (Exception e) {
            log.error("Failed to import skill from standard", e);
            throw new RuntimeException("Failed to import skill", e);
        }
    }

    /**
     * 分享技能到市场
     */
    public SkillShareInfo shareSkill(Long skillId, String shareType) {
        Skill skill = skillService.getById(skillId);
        if (skill == null) {
            throw new IllegalArgumentException("Skill not found: " + skillId);
        }

        // 生成分享代码
        String shareCode = generateShareCode(skill);

        SkillShareInfo info = new SkillShareInfo();
        info.setSkillId(skillId);
        info.setSkillName(skill.getSkillName());
        info.setShareCode(shareCode);
        info.setShareType(shareType); // public, private, link
        info.setShareUrl(generateShareUrl(shareCode));
        info.setStandardFormat(exportSkillAsStandard(skillId));

        log.info("Shared skill: id={}, shareCode={}, type={}", skillId, shareCode, shareType);

        return info;
    }

    /**
     * 通过分享代码获取技能
     */
    public Skill getSkillByShareCode(String shareCode) {
        // TODO: 从数据库或缓存中获取
        // 这里简化实现，实际应该查询分享表
        log.info("Getting skill by share code: {}", shareCode);
        return null;
    }

    /**
     * 搜索公开技能
     */
    public List<Skill> searchPublicSkills(String keyword, int limit) {
        // TODO: 从公开技能库中搜索
        // 这里返回当前租户的技能作为示例
        return skillService.searchSkillsByKeyword(keyword, limit);
    }

    /**
     * 获取热门技能
     */
    public List<Skill> getTrendingSkills(int limit) {
        return skillService.getTopSkills(100L, limit); // TODO: 使用实际租户ID
    }

    /**
     * 推荐技能（基于用户使用历史）
     */
    public List<Skill> recommendSkills(Long userId, int limit) {
        // TODO: 基于用户历史使用记录推荐
        // 这里简化实现，返回热门技能
        return getTrendingSkills(limit);
    }

    /**
     * 生成分享代码
     */
    private String generateShareCode(Skill skill) {
        return String.format("%s_%d_%d",
                skill.getSkillType().substring(0, 3).toUpperCase(),
                skill.getId(),
                System.currentTimeMillis() % 10000);
    }

    /**
     * 生成分享URL
     */
    private String generateShareUrl(String shareCode) {
        // TODO: 从配置获取基础URL
        return "https://agent-xiaoai.com/skills/" + shareCode;
    }

    /**
     * 技能分享信息
     */
    public static class SkillShareInfo {
        private Long skillId;
        private String skillName;
        private String shareCode;
        private String shareType;
        private String shareUrl;
        private String standardFormat;

        // Getters and Setters
public Long getSkillId() { return skillId; }
public void setSkillId(Long skillId) { this.skillId = skillId; }
public String getSkillName() { return skillName; }
public void setSkillName(String skillName) { this.skillName = skillName; }
public String getShareCode() { return shareCode; }
public void setShareCode(String shareCode) { this.shareCode = shareCode; }
public String getShareType() { return shareType; }
public void setShareType(String shareType) { this.shareType = shareType; }
public String getShareUrl() { return shareUrl; }
public void setShareUrl(String shareUrl) { this.shareUrl = shareUrl; }
public String getStandardFormat() { return standardFormat; }
public void setStandardFormat(String standardFormat) { this.standardFormat = standardFormat; }
    }
}
