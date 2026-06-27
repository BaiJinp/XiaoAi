package com.xiaoai.agent.collaboration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.collaboration.entity.QualityGate;
import com.xiaoai.agent.collaboration.model.CreateQualityGateCommand;
import com.xiaoai.agent.collaboration.model.UpdateQualityGateCommand;

import java.util.List;

public interface QualityGateService extends IService<QualityGate> {

    QualityGate createGate(CreateQualityGateCommand command);

    List<QualityGate> listGates(Long sessionId);

    QualityGate passGate(Long sessionId, Long gateId, UpdateQualityGateCommand command);

    QualityGate failGate(Long sessionId, Long gateId, UpdateQualityGateCommand command);

    boolean hasBlockingFailedGate(Long sessionId);

    QualityGate getGate(Long gateId);
}
