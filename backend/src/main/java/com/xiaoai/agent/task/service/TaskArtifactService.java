package com.xiaoai.agent.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.task.entity.TaskArtifact;

import java.util.List;

/**
 * TaskArtifact服务接口
 * <p>
 * 管理任务执行产出的Artifact（如Markdown文档、代码文件等）。
 * 每个Task在成功完成后会产生一个或多个Artifact，
 * 记录产出物的类型、名称、内容文本和元数据信息。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
public interface TaskArtifactService extends IService<TaskArtifact> {

    /**
     * 在当前租户上下文中根据ID获取Artifact，不存在时抛出NOT_FOUND异常。
     *
     * @param artifactId Artifact ID
     * @return TaskArtifact实体
     */
    TaskArtifact getArtifact(Long artifactId);

    /**
     * 列出指定任务的所有Artifact，按创建时间排序。
     *
     * @param taskId 任务ID
     * @return Artifact列表，任务无产出物时返回空列表
     */
    List<TaskArtifact> listByTaskId(Long taskId);
}
