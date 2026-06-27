package com.xiaoai.agent.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.agent.entity.Agent;
import com.xiaoai.agent.agent.model.AgentPageQuery;
import com.xiaoai.agent.agent.mapper.AgentMapper;
import com.xiaoai.agent.agent.model.AgentDraftResponse;
import com.xiaoai.agent.agent.model.CreateAgentDraftCommand;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Agent服务实现类
 * <p>
 * 实现Agent的创建、查询、分页等核心功能。
 * 支持Agent草稿创建、Agent版本管理等操作。
 * 所有操作都在当前租户上下文中执行。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
@Service
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent> implements AgentService {

    /**
     * 根据ID获取Agent
     * <p>
     * 在当前租户上下文中根据ID查询Agent。
     * 如果Agent不存在，抛出NOT_FOUND异常。
     * </p>
     *
     * @param agentId Agent ID
     * @return Agent实体
     * @throws BusinessException 如果Agent不存在
     */
    @Override
    public Agent getAgent(Long agentId) {
        Long tenantId = UserContextHolder.requireTenantId();
        Agent agent = getBaseMapper().selectOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getTenantId, tenantId)
                .eq(Agent::getId, agentId)
                .last("limit 1"));
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent not found");
        }
        return agent;
    }

    /**
     * 创建Agent草稿
     * <p>
     * 创建一个新的Agent草稿，包含基本信息和初始版本。
     * 生成唯一的AgentCode，设置默认的策略配置。
     * 草稿状态的Agent可以进一步编辑和完善。
     * </p>
     *
     * @param command 创建Agent草稿命令，包含Agent名称、描述、类型、角色提示、职责、边界等信息
     * @return Agent草稿响应，包含Agent ID、AgentCode、状态等信息
     */
    @Override
    public AgentDraftResponse createDraft(CreateAgentDraftCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        Agent agent = new Agent();
        agent.setTenantId(tenantId);
        agent.setAgentCode("AGENT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        agent.setAgentName(command.getAgentName());
        agent.setAgentType(defaultText(command.getAgentType(), "project_assistant"));
        agent.setDescription(command.getDescription());
        agent.setRolePrompt(command.getRolePrompt());
        agent.setResponsibilityText(command.getResponsibilityText());
        agent.setBoundaryText(command.getBoundaryText());
        agent.setCapabilityJson(defaultJson(command.getCapabilityJson(), "[]"));
        agent.setToolPolicyJson(defaultJson(command.getToolPolicyJson(), "{}"));
        agent.setContextPolicyJson(defaultJson(command.getContextPolicyJson(), "{}"));
        agent.setMemoryPolicyJson(defaultJson(command.getMemoryPolicyJson(), "{}"));
        agent.setOrchestrationPolicyJson(defaultJson(command.getOrchestrationPolicyJson(), "{\"executionMode\":\"single_agent\"}"));
        agent.setStatus("draft");
        agent.setOwnerUserId(command.getOwnerUserId());
        agent.setConfigJson(buildConfigJson(command));
        save(agent);
        return AgentDraftResponse.builder()
                .agentId(agent.getId())
                .agentCode(agent.getAgentCode())
                .status(agent.getStatus())
                .build();
    }

    /**
     * 分页查询Agent列表
     * <p>
     * 在当前租户上下文中分页查询Agent列表。
     * 支持按状态和名称筛选，按创建时间倒序排序。
     * </p>
     *
     * @param query 分页查询条件，包含页码、每页大小、状态、名称等筛选条件
     * @return 分页响应，包含Agent列表和分页信息（页码、每页大小、总数、记录列表）
     */
    @Override
    public PageResponse<Agent> pageAgents(AgentPageQuery query) {
        Long tenantId = UserContextHolder.requireTenantId();
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
                .eq(Agent::getTenantId, tenantId)
                .eq(StringUtils.hasText(query.getStatus()), Agent::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getAgentName()), Agent::getAgentName, query.getAgentName())
                .orderByDesc(Agent::getCreatedAt);
        Page<Agent> page = page(new Page<>(query.normalizedPageNo(), query.normalizedPageSize()), wrapper);
        return PageResponse.<Agent>builder()
                .pageNo(page.getCurrent())
                .pageSize(page.getSize())
                .total(page.getTotal())
                .records(page.getRecords())
                .build();
    }

    /**
     * 构建配置JSON
     * <p>
     * 根据命令中的模型供应商ID和模型配置ID构建配置JSON。
     * 如果两个ID都为null，返回空JSON对象。
     * </p>
     *
     * @param command 创建Agent草稿命令
     * @return 配置JSON字符串
     */
    private String buildConfigJson(CreateAgentDraftCommand command) {
        if (command.getModelProviderId() == null && command.getModelConfigId() == null) {
            return "{}";
        }
        return "{\"modelProviderId\":" + nullableNumber(command.getModelProviderId())
                + ",\"modelConfigId\":" + nullableNumber(command.getModelConfigId()) + "}";
    }

    /**
     * 获取默认文本值
     * <p>
     * 如果值为空或空白，返回默认值；否则返回原值。
     * </p>
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 最终值
     */
    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 获取默认JSON值
     * <p>
     * 如果值为空或空白，返回默认JSON字符串；否则返回原值。
     * </p>
     *
     * @param value 原始值
     * @param defaultValue 默认JSON字符串
     * @return 最终JSON字符串
     */
    private String defaultJson(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 将Long值转换为JSON可接受的字符串
     * <p>
     * 如果值为null，返回"null"字符串；否则返回值的字符串表示。
     * </p>
     *
     * @param value Long值
     * @return JSON可接受的字符串
     */
    private String nullableNumber(Long value) {
        return value == null ? "null" : value.toString();
    }
}
