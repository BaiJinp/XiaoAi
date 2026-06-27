package com.xiaoai.agent.cost.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.cost.entity.BudgetPolicy;
import com.xiaoai.agent.cost.mapper.BudgetPolicyMapper;
import com.xiaoai.agent.cost.service.BudgetPolicyService;
import org.springframework.stereotype.Service;

@Service
public class BudgetPolicyServiceImpl extends ServiceImpl<BudgetPolicyMapper, BudgetPolicy> implements BudgetPolicyService {
}
