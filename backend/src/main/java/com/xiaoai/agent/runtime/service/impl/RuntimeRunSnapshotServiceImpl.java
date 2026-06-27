package com.xiaoai.agent.runtime.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.runtime.entity.RuntimeRunSnapshot;
import com.xiaoai.agent.runtime.mapper.RuntimeRunSnapshotMapper;
import com.xiaoai.agent.runtime.service.RuntimeRunSnapshotService;
import org.springframework.stereotype.Service;

@Service
public class RuntimeRunSnapshotServiceImpl extends ServiceImpl<RuntimeRunSnapshotMapper, RuntimeRunSnapshot> implements RuntimeRunSnapshotService {
}
