package com.xiaoai.agent.skill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.skill.entity.Skill;
import com.xiaoai.agent.skill.mapper.SkillMapper;
import com.xiaoai.agent.skill.model.CreateSkillCommand;
import com.xiaoai.agent.skill.model.ImproveSkillCommand;
import com.xiaoai.agent.skill.model.SearchSkillQuery;
import com.xiaoai.agent.skill.service.SkillService;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskRun;
import com.xiaoai.agent.task.service.TaskRunService;
import com.xiaoai.agent.task.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 技能服务实现类
 * <p>
 * 实现Agent技能的创建、搜索、使用追踪、自我进化和废弃等全生命周期管理。
 * 核心设计理念：
 * <ul>
 *   <li>技能自我进化：通过recordUsage追踪使用效果，通过improveSkill基于反馈迭代内容</li>
 *   <li>技能自动提取：从成功完成的任务中识别可复用模式（当前为占位实现）</li>
 *   <li>技能质量评估：通过usageCount和successRate双维度衡量技能价值</li>
 * </ul>
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Service
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skill> implements SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillServiceImpl.class);

    private final TaskService taskService;
    private final TaskRunService taskRunService;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数
     *
     * @param taskService    任务服务，用于extractSkillFromTask时获取任务信息
     * @param taskRunService TaskRun服务，用于extractSkillFromTask时获取执行记录
     * @param objectMapper   JSON序列化工具
     */
    @Autowired
    public SkillServiceImpl(TaskService taskService,
                           TaskRunService taskRunService,
                           ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.taskRunService = taskRunService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建技能
     * <p>
     * 初始化所有计数器和指标为0，version为1，status为active。
     * 记录createdAt和updatedAt为当前时间。
     * </p>
     *
     * @param command 创建命令，包含skillCode、skillName、skillType等基本信息和触发条件、执行内容JSON
     * @return 创建后的Skill实体（含生成的ID和时间戳）
     */
    @Override
public Skill createSkill(CreateSkillCommand command) {
        Skill skill = new Skill();
        skill.setTenantId(command.getTenantId());
        skill.setSkillCode(command.getSkillCode());
        skill.setSkillName(command.getSkillName());
        skill.setDescription(command.getDescription());
        skill.setSkillType(command.getSkillType());
        skill.setTriggerConditionJson(command.getTriggerConditionJson());
        skill.setContentJson(command.getContentJson());
        skill.setSourceTaskId(command.getSourceTaskId());
        skill.setTagsJson(command.getTagsJson());
        skill.setCreatedByUserId(command.getCreatedByUserId());

        skill.setUsageCount(0);
        skill.setSuccessCount(0);
        skill.setSuccessRate(0);
        skill.setVersion(1);
        skill.setStatus("active");
        skill.setCreatedAt(OffsetDateTime.now());
        skill.setUpdatedAt(OffsetDateTime.now());

        save(skill);

        log.info("Skill created: code={}, name={}, type={}",
                skill.getSkillCode(), skill.getSkillName(), skill.getSkillType());

        return skill;
    }

    /**
     * 根据skillCode获取active状态的技能，不存在或非active时返回null。
     * 使用租户ID进行隔离，确保不同租户的技能互不可见。
     *
     * @param tenantId  租户ID
     * @param skillCode 技能唯一编码
     * @return Skill实体，或null
     */
    @Override
public Skill getByCode(Long tenantId, String skillCode) {
        return getOne(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getTenantId, tenantId)
                .eq(Skill::getSkillCode, skillCode)
                .eq(Skill::getStatus, "active"));
    }

    /**
     * 搜索技能
     * <p>
     * 在租户的active技能中按keyword模糊匹配skillName、description、tagsJson，
     * 可按skillType精确过滤，可按minSuccessRate过滤低质量技能。
     * 结果按usageCount和successRate双维度倒序排列，可通过limit限制返回数量。
     * </p>
     *
     * @param query 搜索条件
     * @return 技能列表，无匹配时返回空列表
     */
    @Override
public List<Skill> searchSkills(SearchSkillQuery query) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<Skill>()
                .eq(Skill::getTenantId, query.getTenantId())
                .eq(Skill::getStatus, StringUtils.hasText(query.getStatus()) ? query.getStatus() : "active");

        // 关键词搜索
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = "%" + query.getKeyword() + "%";
            wrapper.and(w -> w
                    .like(Skill::getSkillName, query.getKeyword())
                    .or().like(Skill::getDescription, query.getKeyword())
                    .or().like(Skill::getTagsJson, query.getKeyword()));
        }

        // 技能类型过滤
        if (StringUtils.hasText(query.getSkillType())) {
            wrapper.eq(Skill::getSkillType, query.getSkillType());
        }

        // 最小成功率过滤
        if (query.getMinSuccessRate() != null) {
            wrapper.ge(Skill::getSuccessRate, query.getMinSuccessRate());
        }

        // 按使用次数和成功率排序
        wrapper.orderByDesc(Skill::getUsageCount)
               .orderByDesc(Skill::getSuccessRate);

        // 限制返回数量
        if (query.getLimit() != null && query.getLimit() > 0) {
            wrapper.last("limit " + query.getLimit());
        }

        return list(wrapper);
    }

    /**
     * 记录技能的一次使用结果。
     * <p>
     * usageCount每次调用递增1；success为true时successCount同步递增。
     * successRate重新计算为(successCount * 100) / usageCount（整数百分比，非浮点数）。
     * 更新lastUsedAt和updatedAt时间戳。
     * 技能不存在时记录警告日志并静默返回，不抛出异常。
     * </p>
     *
     * @param skillId 技能ID
     * @param success 本次使用是否成功
     */
    @Override
public void recordUsage(Long skillId, boolean success) {
        Skill skill = getById(skillId);
        if (skill == null) {
            log.warn("Skill not found: id={}", skillId);
            return;
        }

        skill.setUsageCount(skill.getUsageCount() + 1);
        if (success) {
            skill.setSuccessCount(skill.getSuccessCount() + 1);
        }

        // 计算成功率
        if (skill.getUsageCount() > 0) {
            skill.setSuccessRate((skill.getSuccessCount() * 100) / skill.getUsageCount());
        }

        skill.setLastUsedAt(OffsetDateTime.now());
        skill.setUpdatedAt(OffsetDateTime.now());

        updateById(skill);

        log.debug("Skill usage recorded: id={}, success={}, usageCount={}, successRate={}",
                skillId, success, skill.getUsageCount(), skill.getSuccessRate());
    }

    /**
     * 基于使用反馈改进技能。
     * <p>
     * 用improvedContentJson替换技能的contentJson（执行内容），并将version递增1。
     * 用于Agent自我进化：当技能执行效果不理想时，根据反馈修正执行逻辑。
     * improvementReason仅记录到日志，不持久化到Skill实体。
     * </p>
     *
     * @param command 改进命令，包含skillId、improvedContentJson和improvementReason
     * @return 更新后的Skill实体（version已递增）
     * @throws IllegalArgumentException 如果技能不存在
     */
    @Override
public Skill improveSkill(ImproveSkillCommand command) {
        Skill skill = getById(command.getSkillId());
        if (skill == null) {
            throw new IllegalArgumentException("Skill not found: " + command.getSkillId());
        }

        // 更新技能内容
        if (StringUtils.hasText(command.getImprovedContentJson())) {
            skill.setContentJson(command.getImprovedContentJson());
        }

        // 版本号递增
        skill.setVersion(skill.getVersion() + 1);
        skill.setUpdatedAt(OffsetDateTime.now());

        updateById(skill);

        log.info("Skill improved: id={}, version={}, reason={}",
                skill.getId(), skill.getVersion(), command.getImprovementReason());

        return skill;
    }

    /**
     * 从成功完成的任务中自动提取可复用技能。
     * <p>
     * 仅处理status=completed的任务，并检查是否有completed状态的TaskRun。
     * 当前版本为占位实现：验证任务和Run存在后直接返回null，
     * 后续将接入LLM分析任务执行过程，识别可复用的工具调用序列和Prompt模式。
     * 任务不存在或非completed状态时记录日志并返回null。
     * </p>
     *
     * @param tenantId 租户ID
     * @param taskId   已完成的任务ID
     * @param userId   触发提取的用户ID
     * @return 提取出的Skill实体，当前返回null
     */
    @Override
public Skill extractSkillFromTask(Long tenantId, Long taskId, Long userId) {
        // 获取任务信息
        Task task = taskService.getById(taskId);
        if (task == null) {
            log.warn("Task not found: id={}", taskId);
            return null;
        }

        // 检查任务是否成功完成
        if (!"completed".equals(task.getStatus())) {
            log.info("Task not completed, skip skill extraction: id={}, status={}",
                    taskId, task.getStatus());
            return null;
        }

        // 获取任务执行记录
        List<TaskRun> runs = taskRunService.list(new LambdaQueryWrapper<TaskRun>()
                .eq(TaskRun::getTaskId, taskId)
                .eq(TaskRun::getStatus, "completed")
                .orderByDesc(TaskRun::getCreatedAt));

        if (runs.isEmpty()) {
            log.info("No completed runs found for task: id={}", taskId);
            return null;
        }

        // TODO: 实现技能提取逻辑
        // 这里需要分析任务执行过程，提取可复用的模式
        // 目前返回 null，后续会实现完整的提取逻辑

        log.info("Skill extraction from task: taskId={}, runs={}", taskId, runs.size());

        return null;
    }

    /**
     * 获取租户下最热门的active技能。
     * 按usageCount和successRate双维度倒序排列，取前limit条。
     *
     * @param tenantId 租户ID
     * @param limit    最大返回数量
     * @return 热门技能列表
     */
    @Override
public List<Skill> getTopSkills(Long tenantId, int limit) {
        return list(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getTenantId, tenantId)
                .eq(Skill::getStatus, "active")
                .orderByDesc(Skill::getUsageCount)
                .orderByDesc(Skill::getSuccessRate)
                .last("limit " + limit));
    }

    /**
     * 废弃技能，将status从active改为deprecated。
     * 废弃后技能不再被searchSkills和getTopSkills查询到，但历史记录保留。
     *
     * @param skillId 技能ID
     * @throws IllegalArgumentException 如果技能不存在
     */
    @Override
public void deprecateSkill(Long skillId) {
        Skill skill = getById(skillId);
        if (skill == null) {
            throw new IllegalArgumentException("Skill not found: " + skillId);
        }

        skill.setStatus("deprecated");
        skill.setUpdatedAt(OffsetDateTime.now());
        updateById(skill);

        log.info("Skill deprecated: id={}, code={}", skillId, skill.getSkillCode());
    }
}
