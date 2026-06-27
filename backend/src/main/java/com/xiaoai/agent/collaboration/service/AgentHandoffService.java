package com.xiaoai.agent.collaboration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.collaboration.entity.AgentHandoff;
import com.xiaoai.agent.collaboration.model.CreateAgentHandoffCommand;

import java.util.List;

public interface AgentHandoffService extends IService<AgentHandoff> {

    AgentHandoff createHandoff(CreateAgentHandoffCommand command);

    List<AgentHandoff> listHandoffs(Long sessionId);

    boolean hasUnacceptedArtifactHandoff(Long sessionId, Long toThreadId, Long artifactId);

    void acceptHandoff(Long handoffId);

    void rejectHandoff(Long handoffId);

    AgentHandoff getHandoff(Long handoffId);
}
