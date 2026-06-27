package com.xiaoai.agent.runtime.gateway;

import com.xiaoai.agent.runtime.model.RunApprovalResultCommand;
import com.xiaoai.agent.runtime.model.RunCancelCommand;
import com.xiaoai.agent.runtime.model.RunResumeCommand;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RunStartResult;
import com.xiaoai.agent.runtime.model.RunUserInputCommand;
import com.xiaoai.agent.runtime.model.RuntimeEvent;

import java.util.List;

public interface RuntimeGateway {

    RunStartResult startRun(RunStartCommand command);

    void cancelRun(RunCancelCommand command);

    void resumeRun(RunResumeCommand command);

    void submitUserInput(RunUserInputCommand command);

    void submitApprovalResult(RunApprovalResultCommand command);

    List<RuntimeEvent> listEvents(Long runId);
}
