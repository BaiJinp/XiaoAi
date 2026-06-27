package com.xiaoai.agent.collaboration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.collaboration.entity.CollaborationPlan;
import com.xiaoai.agent.collaboration.model.SubmitCollaborationPlanCommand;

import java.util.List;

public interface CollaborationPlanService extends IService<CollaborationPlan> {

    CollaborationPlan submitPlan(SubmitCollaborationPlanCommand command);

    List<CollaborationPlan> listPlans(Long sessionId);

    CollaborationPlan getPlan(Long planId);
}
