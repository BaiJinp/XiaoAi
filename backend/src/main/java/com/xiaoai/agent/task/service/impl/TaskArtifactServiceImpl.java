package com.xiaoai.agent.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.mapper.TaskArtifactMapper;
import com.xiaoai.agent.task.service.TaskArtifactService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TaskArtifact服务实现类
 * <p>
 * 在当前租户上下文中提供任务产出物的查询功能。
 * Artifact在任务成功完成时由TaskServiceImpl创建，
 * 记录产出物的类型（markdown/code等）、名称、内容文本和元数据。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Service
public class TaskArtifactServiceImpl extends ServiceImpl<TaskArtifactMapper, TaskArtifact> implements TaskArtifactService {

    /**
     * 在当前租户上下文中根据ID获取Artifact，不存在时抛出NOT_FOUND异常。
     *
     * @param artifactId Artifact ID
     * @return TaskArtifact实体
     * @throws BusinessException 如果Artifact不存在
     */
    @Override
public TaskArtifact getArtifact(Long artifactId) {
        Long tenantId = UserContextHolder.requireTenantId();
        TaskArtifact artifact = getBaseMapper().selectOne(new LambdaQueryWrapper<TaskArtifact>()
                .eq(TaskArtifact::getTenantId, tenantId)
                .eq(TaskArtifact::getId, artifactId)
                .last("limit 1"));
        if (artifact == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Task artifact not found");
        }
        return artifact;
    }

    /**
     * 列出指定任务的所有Artifact，按创建时间倒序排列。
     *
     * @param taskId 任务ID
     * @return Artifact列表
     */
    @Override
public List<TaskArtifact> listByTaskId(Long taskId) {
        Long tenantId = UserContextHolder.requireTenantId();
        return list(new LambdaQueryWrapper<TaskArtifact>()
                .eq(TaskArtifact::getTenantId, tenantId)
                .eq(TaskArtifact::getTaskId, taskId)
                .orderByDesc(TaskArtifact::getCreatedAt));
    }
}
