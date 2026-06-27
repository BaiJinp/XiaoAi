package com.xiaoai.agent.collaboration.service;

import com.xiaoai.agent.collaboration.entity.QualityGate;

public interface CollaborationGateAdvanceService {

    void continueAfterGate(Long sessionId, QualityGate gate);

    void failAfterGate(Long sessionId, QualityGate gate);
}
