package com.xiaoai.agent.scheduled.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.scheduled.entity.ScheduledTask;
import com.xiaoai.agent.scheduled.service.ScheduledTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 定时任务控制器
 */
@RestController
@RequestMapping("/api/v1/scheduled-tasks")
@CrossOrigin(origins = "*")
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    @Autowired
    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    /**
     * 创建定时任务
     */
    @PostMapping
    public ApiResponse<ScheduledTask> createTask(@RequestBody Map<String, Object> request) {
        Long tenantId = Long.valueOf(request.get("tenantId").toString());
        String taskCode = (String) request.get("taskCode");
        String taskName = (String) request.get("taskName");
        String description = (String) request.get("description");
        String cronExpression = (String) request.get("cronExpression");
        String naturalLanguage = (String) request.get("naturalLanguage");
        String taskType = (String) request.get("taskType");
        Long agentId = request.get("agentId") != null ? Long.valueOf(request.get("agentId").toString()) : null;
        String taskConfig = (String) request.get("taskConfig");
        String deliveryTargets = (String) request.get("deliveryTargets");
        Long userId = Long.valueOf(request.get("userId").toString());

        ScheduledTask task = scheduledTaskService.createTask(
                tenantId, taskCode, taskName, description, cronExpression,
                naturalLanguage, taskType, agentId, taskConfig, deliveryTargets, userId);
        return ApiResponse.success(task);
    }

    /**
     * 从自然语言创建任务
     */
    @PostMapping("/from-natural-language")
    public ApiResponse<ScheduledTask> createFromNaturalLanguage(@RequestBody Map<String, Object> request) {
        Long tenantId = Long.valueOf(request.get("tenantId").toString());
        String naturalLanguage = (String) request.get("naturalLanguage");
        String taskType = (String) request.get("taskType");
        Long agentId = request.get("agentId") != null ? Long.valueOf(request.get("agentId").toString()) : null;
        String taskConfig = (String) request.get("taskConfig");
        Long userId = Long.valueOf(request.get("userId").toString());

        ScheduledTask task = scheduledTaskService.createFromNaturalLanguage(
                tenantId, naturalLanguage, taskType, agentId, taskConfig, userId);
        return ApiResponse.success(task);
    }

    /**
     * 根据代码获取任务
     */
    @GetMapping("/{taskCode}")
    public ApiResponse<ScheduledTask> getByCode(@RequestParam Long tenantId, @PathVariable String taskCode) {
        ScheduledTask task = scheduledTaskService.getByCode(tenantId, taskCode);
        if (task == null) {
            return ApiResponse.error("Task not found");
        }
        return ApiResponse.success(task);
    }

    /**
     * 获取用户的所有任务
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<ScheduledTask>> getUserTasks(@RequestParam Long tenantId, @PathVariable Long userId) {
        List<ScheduledTask> tasks = scheduledTaskService.getUserTasks(tenantId, userId);
        return ApiResponse.success(tasks);
    }

    /**
     * 获取 Agent 的所有任务
     */
    @GetMapping("/agent/{agentId}")
    public ApiResponse<List<ScheduledTask>> getAgentTasks(@RequestParam Long tenantId, @PathVariable Long agentId) {
        List<ScheduledTask> tasks = scheduledTaskService.getAgentTasks(tenantId, agentId);
        return ApiResponse.success(tasks);
    }

    /**
     * 获取所有启用的任务
     */
    @GetMapping("/enabled")
    public ApiResponse<List<ScheduledTask>> getEnabledTasks(@RequestParam Long tenantId) {
        List<ScheduledTask> tasks = scheduledTaskService.getEnabledTasks(tenantId);
        return ApiResponse.success(tasks);
    }

    /**
     * 启用/禁用任务
     */
    @PostMapping("/{taskId}/enabled")
    public ApiResponse<String> setEnabled(@PathVariable Long taskId, @RequestParam boolean enabled) {
        scheduledTaskService.setEnabled(taskId, enabled);
        return ApiResponse.success("Task enabled status updated");
    }

    /**
     * 暂停任务
     */
    @PostMapping("/{taskId}/pause")
    public ApiResponse<String> pauseTask(@PathVariable Long taskId) {
        scheduledTaskService.pauseTask(taskId);
        return ApiResponse.success("Task paused");
    }

    /**
     * 恢复任务
     */
    @PostMapping("/{taskId}/resume")
    public ApiResponse<String> resumeTask(@PathVariable Long taskId) {
        scheduledTaskService.resumeTask(taskId);
        return ApiResponse.success("Task resumed");
    }

    /**
     * 立即执行任务
     */
    @PostMapping("/{taskId}/execute")
    public ApiResponse<String> executeNow(@PathVariable Long taskId) {
        scheduledTaskService.executeNow(taskId);
        return ApiResponse.success("Task executed");
    }

    /**
     * 解析自然语言为 Cron 表达式
     */
    @PostMapping("/parse")
    public ApiResponse<Map<String, String>> parseNaturalLanguage(@RequestBody Map<String, String> request) {
        String naturalLanguage = request.get("naturalLanguage");
        String cronExpression = scheduledTaskService.parseNaturalLanguage(naturalLanguage);
        return ApiResponse.success(Map.of(
                "naturalLanguage", naturalLanguage,
                "cronExpression", cronExpression != null ? cronExpression : "Invalid natural language"
        ));
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{taskId}")
    public ApiResponse<String> deleteTask(@PathVariable Long taskId) {
        scheduledTaskService.deleteTask(taskId);
        return ApiResponse.success("Task deleted");
    }
}
