package com.xiaoai.agent.collaboration.service;

import com.xiaoai.agent.collaboration.model.CollaborationPlanValidationResult;

public interface CollaborationPlanValidator {

    CollaborationPlanValidationResult validate(String planJson);
}
