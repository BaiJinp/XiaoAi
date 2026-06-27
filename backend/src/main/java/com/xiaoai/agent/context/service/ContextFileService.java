package com.xiaoai.agent.context.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.context.entity.ContextFile;

import java.util.List;

/**
 * 上下文文件服务接口
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface ContextFileService extends IService<ContextFile> {

    /**
     * 创建上下文文件
     */
    ContextFile createContextFile(Long tenantId, String fileCode, String fileName,
                                  String description, String content, String fileType,
                                  Long projectId, Long agentId, Long userId);

    /**
     * 根据代码获取上下文文件
     */
    ContextFile getByCode(Long tenantId, String fileCode);

    /**
     * 获取项目的上下文文件
     */
    List<ContextFile> getProjectContextFiles(Long tenantId, Long projectId);

    /**
     * 获取 Agent 的上下文文件
     */
    List<ContextFile> getAgentContextFiles(Long tenantId, Long agentId);

    /**
     * 获取自动加载的上下文文件
     */
    List<ContextFile> getAutoLoadFiles(Long tenantId, Long projectId, Long agentId);

    /**
     * 搜索上下文文件
     */
    List<ContextFile> searchContextFiles(Long tenantId, String keyword, String fileType, int limit);

    /**
     * 从 Markdown 文件导入
     */
    ContextFile importFromMarkdown(Long tenantId, String markdown, Long projectId, Long agentId, Long userId);

    /**
     * 导出为 Markdown
     */
    String exportToMarkdown(Long fileId);

    /**
     * 设置自动加载
     */
    void setAutoLoad(Long fileId, boolean autoLoad);

    /**
     * 更新优先级
     */
    void updatePriority(Long fileId, int priority);
}
