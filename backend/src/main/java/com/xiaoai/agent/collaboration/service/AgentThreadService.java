package com.xiaoai.agent.collaboration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.xiaoai.agent.collaboration.model.AgentThreadResponse;
import com.xiaoai.agent.collaboration.model.CreateAgentThreadCommand;
import com.xiaoai.agent.task.model.StartTaskCommand;
import com.xiaoai.agent.task.model.TaskRunResponse;

import java.util.List;

public interface AgentThreadService extends IService<AgentThread> {

    AgentThreadResponse createThread(CreateAgentThreadCommand command);

    TaskRunResponse startThreadTask(Long sessionId, Long threadId, StartTaskCommand command);

    List<AgentThread> listThreads(Long sessionId);

    AgentThread getThread(Long threadId);

    void syncThreadByTaskId(Long taskId);
}
