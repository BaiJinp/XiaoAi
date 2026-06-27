package com.xiaoai.agent.skill.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.skill.entity.Skill;
import com.xiaoai.agent.skill.model.CreateSkillCommand;
import com.xiaoai.agent.skill.model.ImproveSkillCommand;
import com.xiaoai.agent.skill.model.SearchSkillQuery;

import java.util.List;

/**
 * 技能服务接口
 * <p>
 * 管理Agent可复用的技能（Skill），支持技能的创建、搜索、使用追踪、自我进化和废弃。
 * 技能是Agent从成功执行的任务中提炼出的可复用模式，
 * 包含触发条件、执行内容和成功率等元数据，实现Agent的自我学习能力。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface SkillService extends IService<Skill> {

    /**
     * 创建技能
     * <p>
     * 初始化usageCount=0、successCount=0、successRate=0、version=1、status=active。
     * 记录技能的触发条件JSON（triggerConditionJson）和执行内容JSON（contentJson）。
     * </p>
     *
     * @param command 创建命令，包含skillCode、skillName、skillType、触发条件、内容等
     * @return 创建后的Skill实体（含生成的ID和时间戳）
     */
    Skill createSkill(CreateSkillCommand command);

    /**
     * 根据skillCode获取处于active状态的技能（租户隔离），不存在时返回null。
     *
     * @param tenantId  租户ID
     * @param skillCode 技能唯一编码
     * @return Skill实体，不存在或非active时返回null
     */
    Skill getByCode(Long tenantId, String skillCode);

    /**
     * 搜索技能
     * <p>
     * 支持以下过滤和排序：
     * <ul>
     *   <li>keyword - 在skillName、description、tagsJson中模糊匹配</li>
     *   <li>skillType - 精确匹配技能类型</li>
     *   <li>minSuccessRate - 过滤最低成功率</li>
     *   <li>status - 默认active</li>
     * </ul>
     * 按usageCount和successRate双维度倒序排列。
     * </p>
     *
     * @param query 搜索条件
     * @return 技能列表
     */
    List<Skill> searchSkills(SearchSkillQuery query);

    /**
     * 记录技能的一次使用结果，更新usageCount、successCount和successRate。
     * successRate计算方式：(successCount * 100) / usageCount（整数百分比）。
     *
     * @param skillId 技能ID
     * @param success 本次使用是否成功
     */
    void recordUsage(Long skillId, boolean success);

    /**
     * 基于使用反馈改进技能
     * <p>
     * 更新技能的contentJson（执行内容），并将版本号递增。
     * 用于Agent的自我进化：当某次技能执行效果不佳时，根据反馈修改执行逻辑。
     * </p>
     *
     * @param command 改进命令，包含skillId、improvedContentJson和improvementReason
     * @return 更新后的Skill实体（version已递增）
     * @throws IllegalArgumentException 如果技能不存在
     */
    Skill improveSkill(ImproveSkillCommand command);

    /**
     * 从成功完成的任务中自动提取可复用的技能。
     * 仅处理status=completed的任务，通过分析执行过程识别可复用模式。
     * 当前版本为占位实现（返回null），后续将接入LLM分析逻辑。
     *
     * @param tenantId 租户ID
     * @param taskId   已完成的任务ID
     * @param userId   触发提取的用户ID
     * @return 提取出的Skill实体，当前返回null
     */
    Skill extractSkillFromTask(Long tenantId, Long taskId, Long userId);

    /**
     * 获取租户下使用最频繁且成功率最高的active技能列表。
     * 按usageCount和successRate双维度倒序排列，取前limit条。
     *
     * @param tenantId 租户ID
     * @param limit    最大返回数量
     * @return 热门技能列表
     */
    List<Skill> getTopSkills(Long tenantId, int limit);

    /**
     * 废弃技能，将status从active改为deprecated。
     * 废弃后的技能不再参与技能匹配和使用，但历史记录保留。
     *
     * @param skillId 技能ID
     * @throws IllegalArgumentException 如果技能不存在
     */
    void deprecateSkill(Long skillId);
}
