package com.xiaoai.agent.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.model.TaskEventQuery;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * TaskEvent服务接口
 * <p>
 * 提供任务事件的查询和管理功能。
 * TaskEvent记录任务执行过程中产生的所有事件，包括：
 * <ul>
 *   <li>RUN_STARTED / RUN_COMPLETED / RUN_CANCELLED - 运行生命周期事件</li>
 *   <li>APPROVAL_REQUIRED / RUN_SUSPENDED / RUN_RESUMED - 审批相关事件</li>
 *   <li>TOOL_DENIED - 工具拒绝事件（用于安全审计）</li>
 *   <li>ASSISTANT_ARTIFACT / MODEL_RESULT / TOOL_RESULT - 执行产出事件</li>
 * </ul>
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
public interface TaskEventService extends IService<TaskEvent> {

    /**
     * 在当前租户上下文中根据ID获取事件，不存在时抛出NOT_FOUND异常。
     *
     * @param eventId 事件ID
     * @return TaskEvent实体
     */
    TaskEvent getEvent(Long eventId);

    /**
     * 按查询条件列出事件（限制返回数量，默认50条，最大200条）。
     * 目前仅支持eventType=TOOL_DENIED的查询。
     *
     * @param query 查询条件，包含eventType、taskId、runId、agentVersionId、keyword等
     * @return 按ID倒序排列的事件列表
     */
    List<TaskEvent> listEvents(TaskEventQuery query);

    /**
     * 按查询条件分页查询事件。
     *
     * @param query 查询条件（同listEvents）
     * @return 分页结果
     */
    PageResponse<TaskEvent> pageEvents(TaskEventQuery query);

    /**
     * 删除指定时间之前的TOOL_DENIED类型事件（用于定期清理过期审计日志）。
     *
     * @param occurredBefore 截止时间，该时间之前的事件将被删除
     * @return 删除的记录数，occurredBefore为null时返回0
     */
    int removeToolDeniedBefore(OffsetDateTime occurredBefore);
}
