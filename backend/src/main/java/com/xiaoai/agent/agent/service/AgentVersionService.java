package com.xiaoai.agent.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.model.AgentVersionResponse;
import com.xiaoai.agent.agent.model.UpdateAgentConfigCommand;

import java.util.List;

/**
 * Agent版本服务接口
 * <p>
 * 提供Agent版本的创建、查询、更新、发布等版本管理功能。
 * 支持版本工具范围管理、版本发布等核心操作。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface AgentVersionService extends IService<AgentVersion> {

    /**
     * 根据版本ID获取Agent版本
     *
     * @param versionId 版本ID
     * @return AgentVersion实体，如果不存在则返回null
     */
    AgentVersion getVersion(Long versionId);

    /**
     * 根据Agent ID列出所有版本
     *
     * @param agentId Agent ID
     * @return Agent版本响应列表，按创建时间倒序排序
     */
    List<AgentVersionResponse> listVersionsByAgent(Long agentId);

    /**
     * 创建Agent版本
     * <p>
     * 基于当前Agent配置创建新版本，包含工具范围、策略配置等。
     * 新版本状态为draft，可以进一步编辑。
     * </p>
     *
     * @param agentId Agent ID
     * @param command 更新Agent配置命令，包含工具ID列表、策略配置等
     * @return Agent版本响应，包含版本ID、版本号、状态等信息
     */
    AgentVersionResponse createVersion(Long agentId, UpdateAgentConfigCommand command);

    /**
     * 替换版本工具范围
     * <p>
     * 替换指定版本的工具范围配置。
     * 保留现有工具，新增工具，移除工具标记为inactive。
     * 已发布版本不允许修改工具范围。
     * </p>
     *
     * @param agentId Agent ID
     * @param versionId 版本ID
     * @param command 更新Agent配置命令，包含新的工具ID列表
     * @return Agent版本响应
     * @throws BusinessException 如果版本已发布
     */
    AgentVersionResponse replaceVersionTools(Long agentId, Long versionId, UpdateAgentConfigCommand command);

    /**
     * 发布Agent版本
     * <p>
     * 将draft状态的版本发布为published状态。
     * 发布后会更新Agent的currentVersionId和latestStableVersionId。
     * </p>
     *
     * @param agentId Agent ID
     * @param versionId 版本ID
     * @return 发布后的AgentVersion实体
     * @throws BusinessException 如果版本状态不是draft
     */
    AgentVersion publishVersion(Long agentId, Long versionId);
}
