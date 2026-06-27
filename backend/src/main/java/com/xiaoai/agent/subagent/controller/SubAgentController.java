package com.xiaoai.agent.subagent.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.subagent.entity.SubAgent;
import com.xiaoai.agent.subagent.executor.SubAgentExecutor;
import com.xiaoai.agent.subagent.service.SubAgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 子代理控制器
 */
@RestController
@RequestMapping("/api/v1/sub-agents")
@CrossOrigin(origins = "*")
public class SubAgentController {

    private final SubAgentService subAgentService;
    private final SubAgentExecutor subAgentExecutor;

    @Autowired
    public SubAgentController(SubAgentService subAgentService,
                             SubAgentExecutor subAgentExecutor) {
        this.subAgentService = subAgentService;
        this.subAgentExecutor = subAgentExecutor;
    }

    /**
     * 创建子代理
     */
    @PostMapping
    public ApiResponse<SubAgent> createSubAgent(@RequestBody Map<String, Object> request) {
        Long tenantId = Long.valueOf(request.get("tenantId").toString());
        Long parentTaskId = Long.valueOf(request.get("parentTaskId").toString());
        Long parentRunId = Long.valueOf(request.get("parentRunId").toString());
        Long agentId = Long.valueOf(request.get("agentId").toString());
        Long agentVersionId = Long.valueOf(request.get("agentVersionId").toString());
        String taskDescription = (String) request.get("taskDescription");
        Integer priority = request.get("priority") != null ? Integer.valueOf(request.get("priority").toString()) : null;
        Boolean isolated = request.get("isolated") != null ? Boolean.valueOf(request.get("isolated").toString()) : null;
        Long timeoutMs = request.get("timeoutMs") != null ? Long.valueOf(request.get("timeoutMs").toString()) : null;

        SubAgent subAgent = subAgentService.createSubAgent(
                tenantId, parentTaskId, parentRunId, agentId, agentVersionId,
                taskDescription, priority, isolated, timeoutMs);

        return ApiResponse.success(subAgent);
    }

    /**
     * 获取父任务的所有子代理
     */
    @GetMapping("/parent/{parentTaskId}")
    public ApiResponse<List<SubAgent>> getSubAgentsByParent(@PathVariable Long parentTaskId) {
        List<SubAgent> subAgents = subAgentService.getSubAgentsByParent(parentTaskId);
        return ApiResponse.success(subAgents);
    }

    /**
     * 并行执行多个子代理
     */
    @PostMapping("/execute-parallel")
    public ApiResponse<SubAgentExecutor.ParallelExecutionResult> executeParallel(
            @RequestParam Long parentTaskId,
            @RequestBody List<Long> subAgentIds) {

        List<SubAgent> subAgents = subAgentIds.stream()
                .map(subAgentService::getById)
                .filter(subAgent -> subAgent != null)
                .toList();

        SubAgentExecutor.ParallelExecutionResult result = subAgentExecutor.executeParallel(parentTaskId, subAgents);

        return ApiResponse.success(result);
    }

    /**
     * 取消子代理
     */
    @PostMapping("/{subAgentId}/cancel")
    public ApiResponse<String> cancelSubAgent(@PathVariable Long subAgentId) {
        subAgentService.cancelSubAgent(subAgentId);
        return ApiResponse.success("Sub-agent cancelled");
    }

    /**
     * 获取等待中的子代理
     */
    @GetMapping("/pending")
    public ApiResponse<List<SubAgent>> getPendingSubAgents() {
        List<SubAgent> subAgents = subAgentService.getPendingSubAgents();
        return ApiResponse.success(subAgents);
    }

    /**
     * 获取运行中的子代理
     */
    @GetMapping("/running")
    public ApiResponse<List<SubAgent>> getRunningSubAgents() {
        List<SubAgent> subAgents = subAgentService.getRunningSubAgents();
        return ApiResponse.success(subAgents);
    }
}
