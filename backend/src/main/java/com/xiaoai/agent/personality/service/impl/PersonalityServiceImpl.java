package com.xiaoai.agent.personality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.personality.entity.Personality;
import com.xiaoai.agent.personality.mapper.PersonalityMapper;
import com.xiaoai.agent.personality.service.PersonalityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 人格服务实现
 */
@Service
public class PersonalityServiceImpl extends ServiceImpl<PersonalityMapper, Personality>
        implements PersonalityService {

    private static final Logger log = LoggerFactory.getLogger(PersonalityServiceImpl.class);

    @Override
public Personality createPersonality(Long tenantId, String personalityCode, String personalityName,
                                         String description, String content, String personalityType,
                                         Long agentId, Long userId) {
        Personality personality = new Personality();
        personality.setTenantId(tenantId);
        personality.setPersonalityCode(personalityCode);
        personality.setPersonalityName(personalityName);
        personality.setDescription(description);
        personality.setContent(content);
        personality.setPersonalityType(personalityType != null ? personalityType : "custom");
        personality.setAgentId(agentId);
        personality.setCreatedByUserId(userId);
        personality.setIsPublic(false);
        personality.setUsageCount(0);
        personality.setRating(0);
        personality.setStatus("active");
        personality.setCreatedAt(OffsetDateTime.now());
        personality.setUpdatedAt(OffsetDateTime.now());

        save(personality);
        log.info("Created personality: code={}, name={}, type={}",
                personalityCode, personalityName, personalityType);

        return personality;
    }

    @Override
public Personality getByCode(Long tenantId, String personalityCode) {
        return getOne(new LambdaQueryWrapper<Personality>()
                .eq(Personality::getTenantId, tenantId)
                .eq(Personality::getPersonalityCode, personalityCode)
                .eq(Personality::getStatus, "active"));
    }

    @Override
public Personality getAgentPersonality(Long tenantId, Long agentId) {
        return getOne(new LambdaQueryWrapper<Personality>()
                .eq(Personality::getTenantId, tenantId)
                .eq(Personality::getAgentId, agentId)
                .eq(Personality::getStatus, "active")
                .orderByDesc(Personality::getUpdatedAt)
                .last("limit 1"));
    }

    @Override
public void setAgentPersonality(Long tenantId, Long agentId, String personalityCode) {
        Personality personality = getByCode(tenantId, personalityCode);
        if (personality == null) {
            throw new IllegalArgumentException("Personality not found: " + personalityCode);
        }

        // 更新人格的 agentId
        personality.setAgentId(agentId);
        personality.setUpdatedAt(OffsetDateTime.now());
        updateById(personality);

        log.info("Set personality for agent: agentId={}, personalityCode={}",
                agentId, personalityCode);
    }

    @Override
public List<Personality> searchPersonalities(Long tenantId, String keyword, String personalityType,
                                                  Boolean isPublic, int limit) {
        LambdaQueryWrapper<Personality> wrapper = new LambdaQueryWrapper<Personality>()
                .eq(Personality::getTenantId, tenantId)
                .eq(Personality::getStatus, "active");

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Personality::getPersonalityName, keyword)
                    .or().like(Personality::getDescription, keyword)
                    .or().like(Personality::getContent, keyword));
        }

        if (StringUtils.hasText(personalityType)) {
            wrapper.eq(Personality::getPersonalityType, personalityType);
        }

        if (isPublic != null) {
            wrapper.eq(Personality::getIsPublic, isPublic);
        }

        wrapper.orderByDesc(Personality::getUsageCount)
               .orderByDesc(Personality::getRating);

        if (limit > 0) {
            wrapper.last("limit " + limit);
        }

        return list(wrapper);
    }

    @Override
public List<Personality> getTopPersonalities(Long tenantId, int limit) {
        return list(new LambdaQueryWrapper<Personality>()
                .eq(Personality::getTenantId, tenantId)
                .eq(Personality::getStatus, "active")
                .eq(Personality::getIsPublic, true)
                .orderByDesc(Personality::getUsageCount)
                .orderByDesc(Personality::getRating)
                .last("limit " + limit));
    }

    @Override
public void incrementUsage(Long personalityId) {
        Personality personality = getById(personalityId);
        if (personality != null) {
            personality.setUsageCount(personality.getUsageCount() + 1);
            personality.setUpdatedAt(OffsetDateTime.now());
            updateById(personality);
        }
    }

    @Override
public Personality importFromMarkdown(Long tenantId, String markdown, Long userId) {
        // 解析 Markdown 提取人格信息
        String name = extractNameFromMarkdown(markdown);
        String description = extractDescriptionFromMarkdown(markdown);
        String type = extractTypeFromMarkdown(markdown);

        String code = "imported_" + System.currentTimeMillis();

        return createPersonality(tenantId, code, name, description, markdown, type, null, userId);
    }

    @Override
public String exportToMarkdown(Long personalityId) {
        Personality personality = getById(personalityId);
        if (personality == null) {
            throw new IllegalArgumentException("Personality not found: " + personalityId);
        }

        return personality.getContent();
    }

    @Override
public void sharePersonality(Long personalityId) {
        Personality personality = getById(personalityId);
        if (personality != null) {
            personality.setIsPublic(true);
            personality.setUpdatedAt(OffsetDateTime.now());
            updateById(personality);
            log.info("Shared personality: id={}", personalityId);
        }
    }

    @Override
public void unsharePersonality(Long personalityId) {
        Personality personality = getById(personalityId);
        if (personality != null) {
            personality.setIsPublic(false);
            personality.setUpdatedAt(OffsetDateTime.now());
            updateById(personality);
            log.info("Unshared personality: id={}", personalityId);
        }
    }

    /**
     * 从 Markdown 提取名称
     */
    private String extractNameFromMarkdown(String markdown) {
        if (markdown.startsWith("# ")) {
            int end = markdown.indexOf("\n");
            if (end > 2) {
                return markdown.substring(2, end).trim();
            }
        }
        return "Imported Personality";
    }

    /**
     * 从 Markdown 提取描述
     */
    private String extractDescriptionFromMarkdown(String markdown) {
        String[] lines = markdown.split("\n");
        for (int i = 1; i < lines.length && i < 5; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                return line.length() > 200 ? line.substring(0, 200) + "..." : line;
            }
        }
        return "Imported personality";
    }

    /**
     * 从 Markdown 提取类型
     */
    private String extractTypeFromMarkdown(String markdown) {
        String lower = markdown.toLowerCase();
        if (lower.contains("professional") || lower.contains("专业")) {
            return "professional";
        } else if (lower.contains("friendly") || lower.contains("友好")) {
            return "friendly";
        } else if (lower.contains("expert") || lower.contains("专家")) {
            return "expert";
        } else if (lower.contains("creative") || lower.contains("创意")) {
            return "creative";
        }
        return "custom";
    }
}
