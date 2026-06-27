package com.xiaoai.agent.scheduled.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaoai.agent.scheduled.entity.ScheduledTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务 Mapper
 */
@Mapper
public interface ScheduledTaskMapper extends BaseMapper<ScheduledTask> {
}
