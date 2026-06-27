package com.xiaoai.agent.runtime.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.runtime.entity.RuntimeNode;
import com.xiaoai.agent.runtime.mapper.RuntimeNodeMapper;
import com.xiaoai.agent.runtime.service.RuntimeNodeService;
import org.springframework.stereotype.Service;

@Service
public class RuntimeNodeServiceImpl extends ServiceImpl<RuntimeNodeMapper, RuntimeNode> implements RuntimeNodeService {
}
