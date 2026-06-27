package com.xiaoai.agent.subagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.subagent.entity.SubAgent;

import java.util.List;

/**
 * 子代理服务接口
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface SubAgentService extends IService<SubAgent> {

    /**
     * 创建子代理
     */
    SubAgent createSubAgent(Long tenantId, Long parentTaskId, Long parentRunId,
                            Long agentId, Long agentVersionId, String taskDescription,
                            Integer priority, Boolean isolated, Long timeoutMs);

    /**
     * 获取父任务的所有子代理
     */
    List<SubAgent> getSubAgentsByParent(Long parentTaskId);

    /**
     * 更新子代理状态
     */
    void updateStatus(Long subAgentId, String status);

    /**
     * 设置子代理结果
     */
    void setResult(Long subAgentId, String result, Integer tokenUsage);

    /**
     * 设置子代理错误
     */
    void setError(Long subAgentId, String errorMessage);

    /**
     * 取消子代理
     */
    void cancelSubAgent(Long subAgentId);

    /**
     * 获取等待中的子代理
     */
    List<SubAgent> getPendingSubAgents();

    /**
     * 获取运行中的子代理
     */
    List<SubAgent> getRunningSubAgents();
}
