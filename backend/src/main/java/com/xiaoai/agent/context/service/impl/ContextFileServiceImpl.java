package com.xiaoai.agent.context.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.context.entity.ContextFile;
import com.xiaoai.agent.context.mapper.ContextFileMapper;
import com.xiaoai.agent.context.service.ContextFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 上下文文件服务实现
 */
@Service
public class ContextFileServiceImpl extends ServiceImpl<ContextFileMapper, ContextFile>
        implements ContextFileService {

    private static final Logger log = LoggerFactory.getLogger(ContextFileServiceImpl.class);

    @Override
public ContextFile createContextFile(Long tenantId, String fileCode, String fileName,
                                         String description, String content, String fileType,
                                         Long projectId, Long agentId, Long userId) {
        ContextFile contextFile = new ContextFile();
        contextFile.setTenantId(tenantId);
        contextFile.setFileCode(fileCode);
        contextFile.setFileName(fileName);
        contextFile.setDescription(description);
        contextFile.setContent(content);
        contextFile.setFileType(fileType != null ? fileType : "custom");
        contextFile.setProjectId(projectId);
        contextFile.setAgentId(agentId);
        contextFile.setCreatedByUserId(userId);
        contextFile.setAutoLoad(false);
        contextFile.setPriority(0);
        contextFile.setStatus("active");
        contextFile.setCreatedAt(OffsetDateTime.now());
        contextFile.setUpdatedAt(OffsetDateTime.now());

        save(contextFile);
        log.info("Created context file: code={}, name={}, type={}",
                fileCode, fileName, fileType);

        return contextFile;
    }

    @Override
public ContextFile getByCode(Long tenantId, String fileCode) {
        return getOne(new LambdaQueryWrapper<ContextFile>()
                .eq(ContextFile::getTenantId, tenantId)
                .eq(ContextFile::getFileCode, fileCode)
                .eq(ContextFile::getStatus, "active"));
    }

    @Override
public List<ContextFile> getProjectContextFiles(Long tenantId, Long projectId) {
        return list(new LambdaQueryWrapper<ContextFile>()
                .eq(ContextFile::getTenantId, tenantId)
                .eq(ContextFile::getProjectId, projectId)
                .eq(ContextFile::getStatus, "active")
                .orderByDesc(ContextFile::getPriority)
                .orderByDesc(ContextFile::getUpdatedAt));
    }

    @Override
public List<ContextFile> getAgentContextFiles(Long tenantId, Long agentId) {
        return list(new LambdaQueryWrapper<ContextFile>()
                .eq(ContextFile::getTenantId, tenantId)
                .eq(ContextFile::getAgentId, agentId)
                .eq(ContextFile::getStatus, "active")
                .orderByDesc(ContextFile::getPriority)
                .orderByDesc(ContextFile::getUpdatedAt));
    }

    @Override
public List<ContextFile> getAutoLoadFiles(Long tenantId, Long projectId, Long agentId) {
        LambdaQueryWrapper<ContextFile> wrapper = new LambdaQueryWrapper<ContextFile>()
                .eq(ContextFile::getTenantId, tenantId)
                .eq(ContextFile::getStatus, "active")
                .eq(ContextFile::getAutoLoad, true);

        // 获取项目级和 Agent 级的自动加载文件
        if (projectId != null || agentId != null) {
            wrapper.and(w -> {
                if (projectId != null) {
                    w.eq(ContextFile::getProjectId, projectId);
                }
                if (agentId != null) {
                    w.or().eq(ContextFile::getAgentId, agentId);
                }
            });
        }

        return list(wrapper.orderByDesc(ContextFile::getPriority));
    }

    @Override
public List<ContextFile> searchContextFiles(Long tenantId, String keyword, String fileType, int limit) {
        LambdaQueryWrapper<ContextFile> wrapper = new LambdaQueryWrapper<ContextFile>()
                .eq(ContextFile::getTenantId, tenantId)
                .eq(ContextFile::getStatus, "active");

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(ContextFile::getFileName, keyword)
                    .or().like(ContextFile::getDescription, keyword)
                    .or().like(ContextFile::getContent, keyword));
        }

        if (StringUtils.hasText(fileType)) {
            wrapper.eq(ContextFile::getFileType, fileType);
        }

        wrapper.orderByDesc(ContextFile::getPriority)
               .orderByDesc(ContextFile::getUpdatedAt);

        if (limit > 0) {
            wrapper.last("limit " + limit);
        }

        return list(wrapper);
    }

    @Override
public ContextFile importFromMarkdown(Long tenantId, String markdown, Long projectId, Long agentId, Long userId) {
        String name = extractNameFromMarkdown(markdown);
        String description = extractDescriptionFromMarkdown(markdown);
        String type = extractTypeFromMarkdown(markdown);

        String code = "context_" + System.currentTimeMillis();

        return createContextFile(tenantId, code, name, description, markdown, type, projectId, agentId, userId);
    }

    @Override
public String exportToMarkdown(Long fileId) {
        ContextFile contextFile = getById(fileId);
        if (contextFile == null) {
            throw new IllegalArgumentException("Context file not found: " + fileId);
        }

        return contextFile.getContent();
    }

    @Override
public void setAutoLoad(Long fileId, boolean autoLoad) {
        ContextFile contextFile = getById(fileId);
        if (contextFile != null) {
            contextFile.setAutoLoad(autoLoad);
            contextFile.setUpdatedAt(OffsetDateTime.now());
            updateById(contextFile);
            log.info("Set auto load for context file: id={}, autoLoad={}", fileId, autoLoad);
        }
    }

    @Override
public void updatePriority(Long fileId, int priority) {
        ContextFile contextFile = getById(fileId);
        if (contextFile != null) {
            contextFile.setPriority(priority);
            contextFile.setUpdatedAt(OffsetDateTime.now());
            updateById(contextFile);
            log.info("Updated priority for context file: id={}, priority={}", fileId, priority);
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
        return "Context File";
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
        return "Context file";
    }

    /**
     * 从 Markdown 提取类型
     */
    private String extractTypeFromMarkdown(String markdown) {
        String lower = markdown.toLowerCase();
        if (lower.contains("project") || lower.contains("项目")) {
            return "project";
        } else if (lower.contains("agent") || lower.contains("助手")) {
            return "agent";
        } else if (lower.contains("workspace") || lower.contains("工作区")) {
            return "workspace";
        }
        return "custom";
    }
}
