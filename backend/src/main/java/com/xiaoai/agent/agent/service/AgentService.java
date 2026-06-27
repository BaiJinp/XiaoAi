package com.xiaoai.agent.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.agent.entity.Agent;
import com.xiaoai.agent.agent.model.AgentPageQuery;
import com.xiaoai.agent.agent.model.AgentDraftResponse;
import com.xiaoai.agent.agent.model.CreateAgentDraftCommand;
import com.xiaoai.agent.common.api.PageResponse;

/**
 * Agent服务接口
 * <p>
 * 提供Agent的创建、查询、分页等核心功能。
 * 支持Agent草稿创建、Agent版本管理等操作。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface AgentService extends IService<Agent> {

    /**
     * 根据ID获取Agent
     *
     * @param agentId Agent ID
     * @return Agent实体，如果不存在则返回null
     */
    Agent getAgent(Long agentId);

    /**
     * 创建Agent草稿
     * <p>
     * 创建一个新的Agent草稿，包含基本信息和初始版本。
     * 草稿状态的Agent可以进一步编辑和完善。
     * </p>
     *
     * @param command 创建Agent草稿命令，包含Agent名称、描述、类型等信息
     * @return Agent草稿响应，包含Agent ID、版本号等信息
     */
    AgentDraftResponse createDraft(CreateAgentDraftCommand command);

    /**
     * 分页查询Agent列表
     *
     * @param query 分页查询条件，包含页码、每页大小、筛选条件等
     * @return 分页响应，包含Agent列表和分页信息
     */
    PageResponse<Agent> pageAgents(AgentPageQuery query);
}
