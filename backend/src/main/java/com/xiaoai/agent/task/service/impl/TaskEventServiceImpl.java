package com.xiaoai.agent.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.mapper.TaskEventMapper;
import com.xiaoai.agent.task.model.TaskEventQuery;
import com.xiaoai.agent.task.service.TaskEventService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * TaskEvent服务实现类
 * <p>
 * 在当前租户上下文中实现任务事件的查询和清理功能。
 * 目前仅支持eventType=TOOL_DENIED的查询（用于工具拒绝审计），
 * 其他eventType会抛出BAD_REQUEST异常。
 * 支持按taskId、runId、agentVersionId、keyword进行过滤，
 * agentVersionId通过payloadJson字符串匹配查询。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Service
public class TaskEventServiceImpl extends ServiceImpl<TaskEventMapper, TaskEvent> implements TaskEventService {

    /** 当前支持的查询事件类型：工具拒绝事件 */
    private static final String QUERY_EVENT_TYPE_TOOL_DENIED = "TOOL_DENIED";

    /** 默认查询条数限制 */
    private static final int DEFAULT_QUERY_LIMIT = 50;

    /** 最大查询条数限制 */
    private static final int MAX_QUERY_LIMIT = 200;

    /**
     * 在当前租户上下文中根据ID获取事件，不存在时抛出NOT_FOUND异常。
     *
     * @param eventId 事件ID
     * @return TaskEvent实体
     */
    @Override
public TaskEvent getEvent(Long eventId) {
        Long tenantId = UserContextHolder.requireTenantId();
        TaskEvent event = getBaseMapper().selectOne(new LambdaQueryWrapper<TaskEvent>()
                .eq(TaskEvent::getTenantId, tenantId)
                .eq(TaskEvent::getId, eventId)
                .last("limit 1"));
        if (event == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Task event not found");
        }
        return event;
    }

    /**
     * 按查询条件列出事件，限制返回条数（默认50，最大200）。
     * 通过buildQueryWrapper构建查询条件，按ID倒序排列。
     *
     * @param query 查询条件
     * @return 事件列表
     */
    @Override
public List<TaskEvent> listEvents(TaskEventQuery query) {
        int queryLimit = normalizeLimit(query == null ? null : query.getLimit());
        LambdaQueryWrapper<TaskEvent> wrapper = buildQueryWrapper(query)
                .last("limit " + queryLimit);
        return getBaseMapper().selectList(wrapper);
    }

    /**
     * 按查询条件分页查询事件，使用query的normalizedPageNo/normalizedPageSize。
     *
     * @param query 查询条件
     * @return 分页结果，包含事件列表和总数
     */
    @Override
public PageResponse<TaskEvent> pageEvents(TaskEventQuery query) {
        LambdaQueryWrapper<TaskEvent> wrapper = buildQueryWrapper(query);
        Page<TaskEvent> page = page(new Page<>(query.normalizedPageNo(), query.normalizedPageSize()), wrapper);
        return PageResponse.<TaskEvent>builder()
                .pageNo(page.getCurrent())
                .pageSize(page.getSize())
                .total(page.getTotal())
                .records(page.getRecords())
                .build();
    }

    /**
     * 删除指定时间之前的TOOL_DENIED类型事件，用于定期清理过期审计日志。
     * occurredBefore为null时返回0，不执行删除操作。
     *
     * @param occurredBefore 截止时间
     * @return 删除的记录数
     */
    @Override
public int removeToolDeniedBefore(OffsetDateTime occurredBefore) {
        if (occurredBefore == null) {
            return 0;
        }
        return getBaseMapper().delete(new LambdaQueryWrapper<TaskEvent>()
                .eq(TaskEvent::getEventType, QUERY_EVENT_TYPE_TOOL_DENIED)
                .lt(TaskEvent::getOccurredAt, occurredBefore));
    }

    /**
     * 构建TaskEvent查询条件包装器。
     * <p>
     * 校验eventType必须为TOOL_DENIED（当前唯一支持的类型），否则抛出BAD_REQUEST。
     * 支持的过滤条件：
     * <ul>
     *   <li>taskId - 精确匹配任务ID</li>
     *   <li>runId - 精确匹配Run ID</li>
     *   <li>agentVersionId - 通过payloadJson字符串模糊匹配（LIKE查询）</li>
     *   <li>keyword - 在payloadJson和eventSummary中模糊搜索</li>
     * </ul>
     * 结果按ID倒序排列。
     * </p>
     *
     * @param query 查询条件
     * @return LambdaQueryWrapper
     * @throws BusinessException 如果eventType为空或不是TOOL_DENIED
     */
    private LambdaQueryWrapper<TaskEvent> buildQueryWrapper(TaskEventQuery query) {
        Long tenantId = UserContextHolder.requireTenantId();
        String eventType = query == null ? null : query.getEventType();
        if (!StringUtils.hasText(eventType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Event type is required");
        }
        if (!QUERY_EVENT_TYPE_TOOL_DENIED.equals(eventType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported task event query type");
        }
        Long taskId = query.getTaskId();
        Long runId = query.getRunId();
        Long agentVersionId = query.getAgentVersionId();
        LambdaQueryWrapper<TaskEvent> wrapper = new LambdaQueryWrapper<TaskEvent>()
                .eq(TaskEvent::getTenantId, tenantId)
                .eq(TaskEvent::getEventType, eventType)
                .eq(taskId != null, TaskEvent::getTaskId, taskId)
                .eq(runId != null, TaskEvent::getRunId, runId);
        if (agentVersionId != null) {
            wrapper.and(nested -> nested
                    .like(TaskEvent::getPayloadJson, "\"agentVersionId\":" + agentVersionId + ",")
                    .or()
                    .like(TaskEvent::getPayloadJson, "\"agentVersionId\":" + agentVersionId + "}"));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(nested -> nested
                    .like(TaskEvent::getPayloadJson, keyword)
                    .or()
                    .like(TaskEvent::getEventSummary, keyword));
        }
        return wrapper.orderByDesc(TaskEvent::getId);
    }

    /**
     * 规范化查询条数限制：null或<=0时使用默认值50，超过上限时截断为200。
     *
     * @param limit 请求的条数
     * @return 规范化后的条数（范围：1~200）
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_QUERY_LIMIT;
        }
        return Math.min(limit, MAX_QUERY_LIMIT);
    }
}
