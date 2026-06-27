package com.xiaoai.agent.agent.service;

import com.xiaoai.agent.agent.model.CreateMainAgentCommand;
import com.xiaoai.agent.agent.model.MainAgentPreviewResponse;
import com.xiaoai.agent.agent.model.MainAgentSetupResponse;
import com.xiaoai.agent.agent.model.MainAgentTrialRunCommand;
import com.xiaoai.agent.agent.model.MainAgentTrialRunResponse;

public interface MainAgentTemplateService {

    MainAgentPreviewResponse preview();

    MainAgentSetupResponse createDraft(CreateMainAgentCommand command);

    MainAgentTrialRunResponse trialRun(MainAgentTrialRunCommand command);
}
