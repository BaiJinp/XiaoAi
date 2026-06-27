package com.xiaoai.agent.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.model.CancelTaskCommand;
import com.xiaoai.agent.task.model.CreateTaskCommand;
import com.xiaoai.agent.task.model.StartTaskCommand;
import com.xiaoai.agent.task.model.SuspendForApprovalCommand;
import com.xiaoai.agent.task.model.TaskPageQuery;
import com.xiaoai.agent.task.model.TaskCreateResponse;
import com.xiaoai.agent.task.model.TaskRunResponse;
import com.xiaoai.agent.task.model.ResumeTaskCommand;

import java.util.List;

/**
 * 任务服务接口
 * <p>
 * 提供任务的创建、启动、取消、暂停、恢复等生命周期管理功能。
 * 支持任务事件查询、审批挂起等核心操作。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface TaskService extends IService<Task> {

    /**
     * 分页查询任务列表
     *
     * @param query 分页查询条件，包含页码、每页大小、状态筛选等
     * @return 分页响应，包含任务列表和分页信息
     */
    PageResponse<Task> pageTasks(TaskPageQuery query);

    /**
     * 根据ID获取任务
     *
     * @param taskId 任务ID
     * @return Task实体，如果不存在则抛出异常
     * @throws BusinessException 如果任务不存在
     */
    Task getTask(Long taskId);

    /**
     * 创建任务
     * <p>
     * 创建一个新的任务，包含任务的基本信息和配置。
     * 任务创建后处于pending状态，需要调用startTask启动。
     * </p>
     *
     * @param command 创建任务命令，包含Agent ID、任务类型、输入文本等
     * @return 任务创建响应，包含任务ID和状态
     */
    TaskCreateResponse createTask(CreateTaskCommand command);

    /**
     * 启动任务
     * <p>
     * 启动pending状态的任务，创建TaskRun并开始执行。
     * 任务启动后会触发Agent循环执行器执行任务逻辑。
     * </p>
     *
     * @param taskId 任务ID
     * @param command 启动任务命令，包含运行时配置
     * @return 任务运行响应，包含Run ID和状态
     * @throws BusinessException 如果任务状态不是pending
     */
    TaskRunResponse startTask(Long taskId, StartTaskCommand command);

    /**
     * 取消任务
     * <p>
     * 取消正在运行或等待中的任务。
     * 取消后会更新任务状态为cancelled，并终止所有相关的TaskRun。
     * </p>
     *
     * @param taskId 任务ID
     * @param command 取消任务命令，包含取消原因
     */
    void cancelTask(Long taskId, CancelTaskCommand command);

    /**
     * 挂起任务等待审批
     * <p>
     * 将任务挂起等待审批，通常用于工具调用需要审批的场景。
     * 挂起后任务状态变为suspended，需要审批通过后调用resumeTask恢复。
     * </p>
     *
     * @param taskId 任务ID
     * @param command 挂起命令，包含审批请求信息
     */
    void suspendForApproval(Long taskId, SuspendForApprovalCommand command);

    /**
     * 恢复任务
     * <p>
     * 恢复被挂起的任务，通常在审批通过后调用。
     * 恢复后任务状态变为running，继续执行之前挂起的步骤。
     * </p>
     *
     * @param taskId 任务ID
     * @param command 恢复任务命令，包含审批结果
     */
    void resumeTask(Long taskId, ResumeTaskCommand command);

    /**
     * 列出任务的所有事件
     *
     * @param taskId 任务ID
     * @return 任务事件列表，按时间顺序排序
     */
    List<TaskEvent> listTaskEvents(Long taskId);

    /**
     * 列出指定事件ID之后的任务事件
     * <p>
     * 用于增量获取任务事件，支持长轮询和SSE推送。
     * </p>
     *
     * @param taskId 任务ID
     * @param lastEventId 最后已知的事件ID
     * @return 新产生的任务事件列表
     */
    List<TaskEvent> listTaskEventsAfter(Long taskId, Long lastEventId);
}
