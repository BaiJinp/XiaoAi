package com.xiaoai.agent.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaoai.agent.skill.entity.Skill;
import org.apache.ibatis.annotations.Mapper;

/**
 * 技能 Mapper
 */
@Mapper
public interface SkillMapper extends BaseMapper<Skill> {
}
