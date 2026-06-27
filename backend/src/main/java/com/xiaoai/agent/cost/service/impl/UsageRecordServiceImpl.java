package com.xiaoai.agent.cost.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.cost.entity.UsageRecord;
import com.xiaoai.agent.cost.mapper.UsageRecordMapper;
import com.xiaoai.agent.cost.service.UsageRecordService;
import org.springframework.stereotype.Service;

@Service
public class UsageRecordServiceImpl extends ServiceImpl<UsageRecordMapper, UsageRecord> implements UsageRecordService {
}
