package com.xiaoai.agent.personality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaoai.agent.personality.entity.Personality;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人格 Mapper
 */
@Mapper
public interface PersonalityMapper extends BaseMapper<Personality> {
}
