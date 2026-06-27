package com.xiaoai.agent.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaoai.agent.memory.entity.AgentMemory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentMemoryMapper extends BaseMapper<AgentMemory> {
}
