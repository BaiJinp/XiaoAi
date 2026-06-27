package com.xiaoai.agent.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.agent.entity.AgentToolBinding;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.mapper.AgentVersionMapper;
import com.xiaoai.agent.agent.model.AgentVersionResponse;
import com.xiaoai.agent.agent.model.UpdateAgentConfigCommand;
import com.xiaoai.agent.agent.service.AgentToolBindingService;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent版本服务实现类
 * <p>
 * 实现Agent版本的创建、查询、更新、发布等版本管理功能。
 * 支持版本工具范围管理、版本发布等核心操作。
 * 所有操作都在当前租户上下文中执行，并保证事务一致性。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
@Service
public class AgentVersionServiceImpl extends ServiceImpl<AgentVersionMapper, AgentVersion> implements AgentVersionService {

    private final ToolConfigService toolConfigService;
    private final AgentToolBindingService agentToolBindingService;
    private final ObjectMapper objectMapper;

    /**
     * 默认构造函数（用于测试）
     */
    public AgentVersionServiceImpl() {
        this(null, null, new ObjectMapper());
    }

    /**
     * 构造函数
     *
     * @param toolConfigService 工具配置服务
     * @param agentToolBindingService Agent工具绑定服务
     * @param ObjectMapper JSON序列化工具
     */
    @Autowired
    public AgentVersionServiceImpl(ToolConfigService toolConfigService,
                                   AgentToolBindingService agentToolBindingService,
                                   ObjectMapper objectMapper) {
        this.toolConfigService = toolConfigService;
        this.agentToolBindingService = agentToolBindingService;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据版本ID获取Agent版本
     * <p>
     * 在当前租户上下文中根据ID查询Agent版本。
     * 如果版本不存在，抛出NOT_FOUND异常。
     * </p>
     *
     * @param versionId 版本ID
     * @return AgentVersion实体
     * @throws BusinessException 如果版本不存在
     */
    @Override
    public AgentVersion getVersion(Long versionId) {
        Long tenantId = UserContextHolder.requireTenantId();
        AgentVersion version = getBaseMapper().selectOne(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getTenantId, tenantId)
                .eq(AgentVersion::getId, versionId)
                .last("limit 1"));
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent version not found");
        }
        return version;
    }

    /**
     * 根据Agent ID列出所有版本
     * <p>
     * 在当前租户上下文中查询指定Agent的所有版本。
     * 按创建时间倒序排序，并解析每个版本的工具范围。
     * </p>
     *
     * @param agentId Agent ID
     * @return Agent版本响应列表，包含版本信息和工具列表
     */
    @Override
    public List<AgentVersionResponse> listVersionsByAgent(Long agentId) {
        Long tenantId = UserContextHolder.requireTenantId();
        return list(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getTenantId, tenantId)
                .eq(AgentVersion::getAgentId, agentId)
                .orderByDesc(AgentVersion::getCreatedAt))
                .stream()
                .map(version -> toResponse(version, parseToolsFromScope(version.getToolScopeJson())))
                .toList();
    }

    /**
     * 发布Agent版本
     * <p>
     * 将draft状态的版本发布为published状态。
     * 验证版本是否属于指定Agent，如果版本状态不是draft则抛出异常。
     * 发布操作在事务中执行，保证数据一致性。
     * </p>
     *
     * @param agentId Agent ID
     * @param versionId 版本ID
     * @return 发布后的AgentVersion实体
     * @throws BusinessException 如果版本不属于指定Agent或状态不是draft
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentVersion publishVersion(Long agentId, Long versionId) {
        AgentVersion version = getVersion(versionId);
        if (!agentId.equals(version.getAgentId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent version does not belong to agent");
        }
        if (!"published".equals(version.getVersionStatus())) {
            version.setVersionStatus("published");
            updateById(version);
        }
        return version;
    }

    /**
     * 创建Agent版本
     * <p>
     * 基于当前Agent配置创建新版本，包含工具范围、策略配置等。
     * 生成基于时间戳的版本号（格式：vyyyyMMddHHmmss）。
     * 新版本状态为draft，可以进一步编辑。
     * 创建版本后会自动绑定工具到AgentToolBinding表。
     * 创建操作在事务中执行，保证数据一致性。
     * </p>
     *
     * @param agentId Agent ID
     * @param command 更新Agent配置命令，包含工具ID列表、角色提示、职责、边界、策略配置等
     * @return Agent版本响应，包含版本ID、版本号、状态、工具列表等信息
     * @throws BusinessException 如果工具解析失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentVersionResponse createVersion(Long agentId, UpdateAgentConfigCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        List<ToolConfig> tools = resolveTools(command.getToolIds());
        AgentVersion version = new AgentVersion();
        version.setTenantId(tenantId);
        version.setAgentId(agentId);
        version.setVersionNo("v" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        version.setVersionStatus("draft");
        version.setRolePrompt(command.getRolePrompt());
        version.setResponsibilityText(command.getResponsibilityText());
        version.setBoundaryText(command.getBoundaryText());
        version.setConfigJson(defaultJson(command.getConfigJson(), "{}"));
        version.setKnowledgeScopeJson(defaultJson(command.getKnowledgeScopeJson(), "[]"));
        version.setToolScopeJson(toToolScopeJson(command, tools));
        version.setPermissionPolicyJson(defaultJson(command.getPermissionPolicyJson(), "{}"));
        version.setBudgetPolicyJson(defaultJson(command.getBudgetPolicyJson(), "{}"));
        version.setRuntimeSnapshotJson(toRuntimeSnapshotJson(command));
        save(version);
        bindVersionTools(tenantId, agentId, version.getId(), tools);
        return toResponse(version, tools);
    }

    /**
     * 替换版本工具范围
     * <p>
     * 替换指定版本的工具范围配置。
     * 验证版本是否属于指定Agent，只有draft状态的版本允许修改工具范围。
     * 解析工具ID列表，更新版本的toolScopeJson，并同步AgentToolBinding表。
     * 替换操作在事务中执行，保证数据一致性。
     * </p>
     *
     * @param agentId Agent ID
     * @param versionId 版本ID
     * @param command 更新Agent配置命令，包含新的工具ID列表
     * @return Agent版本响应，包含更新后的版本信息和工具列表
     * @throws BusinessException 如果版本不属于指定Agent或状态不是draft
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentVersionResponse replaceVersionTools(Long agentId, Long versionId, UpdateAgentConfigCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        AgentVersion version = getVersion(versionId);
        if (!agentId.equals(version.getAgentId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent version does not belong to agent");
        }
        if (!"draft".equals(version.getVersionStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only draft agent version can update tool scope");
        }
        List<ToolConfig> tools = resolveTools(command == null ? null : command.getToolIds());
        version.setToolScopeJson(toToolScopeJson(tools, "[]"));
        updateById(version);
        syncVersionTools(tenantId, agentId, versionId, tools);
        return toResponse(version, tools);
    }

    /**
     * 将AgentVersion实体转换为响应对象
     *
     * @param version AgentVersion实体
     * @param tools 工具配置列表
     * @return AgentVersionResponse响应对象
     */
    private AgentVersionResponse toResponse(AgentVersion version, List<ToolConfig> tools) {
        return AgentVersionResponse.builder()
                .agentVersionId(version.getId())
                .versionNo(version.getVersionNo())
                .versionStatus(version.getVersionStatus())
                .runtimeSnapshotJson(defaultJson(version.getRuntimeSnapshotJson(), "{}"))
                .modelPolicyJson(policyJson(version.getRuntimeSnapshotJson(), "modelPolicy"))
                .toolPolicyJson(policyJson(version.getRuntimeSnapshotJson(), "toolPolicy"))
                .contextPolicyJson(policyJson(version.getRuntimeSnapshotJson(), "contextPolicy"))
                .memoryPolicyJson(policyJson(version.getRuntimeSnapshotJson(), "memoryPolicy"))
                .orchestrationPolicyJson(policyJson(version.getRuntimeSnapshotJson(), "orchestrationPolicy"))
                .toolIds(tools.stream().map(ToolConfig::getId).toList())
                .build();
    }

    /**
     * 解析工具ID列表为ToolConfig列表
     * <p>
     * 根据工具ID列表查询ToolConfig实体。
     * 去重处理，避免重复查询。
     * 如果ToolConfigService不可用，抛出系统异常。
     * </p>
     *
     * @param toolIds 工具ID列表
     * @return ToolConfig列表
     * @throws BusinessException 如果ToolConfigService不可用
     */
    private List<ToolConfig> resolveTools(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }
        if (toolConfigService == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Tool config service is not available");
        }
        return toolIds.stream()
                .distinct()
                .map(toolConfigService::getToolConfig)
                .toList();
    }

    /**
     * 从工具范围JSON解析工具列表
     * <p>
     * 解析toolScopeJson中的工具ID列表。
     * 如果JSON格式不正确，返回空列表。
     * </p>
     *
     * @param toolScopeJson 工具范围JSON字符串
     * @return ToolConfig列表（仅包含ID）
     */
    private List<ToolConfig> parseToolsFromScope(String toolScopeJson) {
        if (toolScopeJson == null || toolScopeJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(toolScopeJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<ToolConfig> tools = new java.util.ArrayList<>();
            for (JsonNode item : root) {
                JsonNode toolId = item.get("toolId");
                if (toolId == null || !toolId.canConvertToLong()) {
                    continue;
                }
                ToolConfig tool = new ToolConfig();
                tool.setId(toolId.asLong());
                tools.add(tool);
            }
            return tools;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * 将命令和工具列表转换为工具范围JSON
     *
     * @param command 更新Agent配置命令
     * @param tools 工具配置列表
     * @return 工具范围JSON字符串
     */
    private String toToolScopeJson(UpdateAgentConfigCommand command, List<ToolConfig> tools) {
        if (tools.isEmpty()) {
            return defaultJson(command.getToolScopeJson(), "[]");
        }
        return toToolScopeJson(tools, "[]");
    }

    /**
     * 将工具列表转换为工具范围JSON
     * <p>
     * 生成包含toolId、toolCode、riskLevel的JSON数组。
     * 如果工具列表为空，返回指定的空值。
     * </p>
     *
     * @param tools 工具配置列表
     * @param emptyValue 工具列表为空时返回的值
     * @return 工具范围JSON字符串
     * @throws BusinessException 如果JSON序列化失败
     */
    private String toToolScopeJson(List<ToolConfig> tools, String emptyValue) {
        if (tools.isEmpty()) {
            return emptyValue;
        }
        try {
            List<Map<String, Object>> scope = tools.stream()
                    .map(tool -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("toolId", tool.getId());
                        item.put("toolCode", tool.getToolCode());
                        item.put("riskLevel", tool.getRiskLevel());
                        return item;
                    })
                    .toList();
            return objectMapper.writeValueAsString(scope);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Build tool scope failed");
        }
    }

    /**
     * 将命令转换为运行时快照JSON
     * <p>
     * 构建包含modelPolicy、toolPolicy、contextPolicy、memoryPolicy、orchestrationPolicy的运行时快照。
     * 如果所有策略都为空，返回空JSON对象。
     * </p>
     *
     * @param command 更新Agent配置命令
     * @return 运行时快照JSON字符串
     * @throws BusinessException 如果策略JSON格式不正确或序列化失败
     */
    private String toRuntimeSnapshotJson(UpdateAgentConfigCommand command) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        putPolicy(snapshot, "modelPolicy", command.getModelPolicyJson());
        putPolicy(snapshot, "toolPolicy", command.getToolPolicyJson());
        putPolicy(snapshot, "contextPolicy", command.getContextPolicyJson());
        putPolicy(snapshot, "memoryPolicy", command.getMemoryPolicyJson());
        putPolicy(snapshot, "orchestrationPolicy", command.getOrchestrationPolicyJson());
        if (snapshot.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Build runtime snapshot failed");
        }
    }

    /**
     * 将策略JSON添加到运行时快照中
     * <p>
     * 验证策略JSON是否为有效的JSON对象，并添加到快照节点中。
     * 如果策略JSON为空或空白，跳过添加。
     * </p>
     *
     * @param snapshot 运行时快照节点
     * @param fieldName 字段名称
     * @param policyJson 策略JSON字符串
     * @throws BusinessException 如果策略JSON不是对象或格式不正确
     */
    private void putPolicy(ObjectNode snapshot, String fieldName, String policyJson) {
        if (policyJson == null || policyJson.isBlank()) {
            return;
        }
        try {
            JsonNode policy = objectMapper.readTree(policyJson);
            if (!policy.isObject()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent policy JSON must be object");
            }
            snapshot.set(fieldName, policy);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent policy JSON must be valid JSON");
        }
    }

    /**
     * 从运行时快照JSON中提取指定字段的策略JSON
     *
     * @param runtimeSnapshotJson 运行时快照JSON字符串
     * @param fieldName 字段名称
     * @return 策略JSON字符串，如果不存在或格式不正确则返回"{}"
     */
    private String policyJson(String runtimeSnapshotJson, String fieldName) {
        if (runtimeSnapshotJson == null || runtimeSnapshotJson.isBlank()) {
            return "{}";
        }
        try {
            JsonNode root = objectMapper.readTree(runtimeSnapshotJson);
            JsonNode policy = root.get(fieldName);
            return policy != null && policy.isObject() ? objectMapper.writeValueAsString(policy) : "{}";
        } catch (Exception ignored) {
            return "{}";
        }
    }

    /**
     * 绑定版本工具
     * <p>
     * 为Agent版本绑定工具，创建或更新AgentToolBinding记录。
     * 如果AgentToolBindingService不可用，抛出系统异常。
     * 对于每个工具，检查是否已存在绑定记录，如果不存在则创建新记录。
     * 设置绑定状态为active，策略JSON标记来源为agent_version。
     * </p>
     *
     * @param tenantId 租户ID
     * @param agentId Agent ID
     * @param agentVersionId Agent版本ID
     * @param tools 工具配置列表
     * @throws BusinessException 如果AgentToolBindingService不可用
     */
    private void bindVersionTools(Long tenantId, Long agentId, Long agentVersionId, List<ToolConfig> tools) {
        if (tools.isEmpty()) {
            return;
        }
        if (agentToolBindingService == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent tool binding service is not available");
        }
        for (ToolConfig tool : tools) {
            AgentToolBinding binding = agentToolBindingService.getOne(new LambdaQueryWrapper<AgentToolBinding>()
                    .eq(AgentToolBinding::getTenantId, tenantId)
                    .eq(AgentToolBinding::getAgentId, agentId)
                    .eq(AgentToolBinding::getAgentVersionId, agentVersionId)
                    .eq(AgentToolBinding::getToolId, tool.getId())
                    .last("limit 1"));
            if (binding == null) {
                binding = new AgentToolBinding();
                binding.setTenantId(tenantId);
                binding.setAgentId(agentId);
                binding.setAgentVersionId(agentVersionId);
                binding.setToolId(tool.getId());
            }
            binding.setBindingStatus("active");
            binding.setPolicyJson("{\"source\":\"agent_version\"}");
            agentToolBindingService.saveOrUpdate(binding);
        }
    }

    /**
     * 同步版本工具
     * <p>
     * 同步Agent版本的工具绑定记录。
     * 对于新工具，创建绑定记录并设置状态为active。
     * 对于已存在的工具，更新绑定状态为active。
     * 对于不在目标工具列表中的工具，将绑定状态设置为inactive。
     * 如果AgentToolBindingService不可用，抛出系统异常。
     * </p>
     *
     * @param tenantId 租户ID
     * @param agentId Agent ID
     * @param agentVersionId Agent版本ID
     * @param tools 目标工具配置列表
     * @throws BusinessException 如果AgentToolBindingService不可用
     */
    private void syncVersionTools(Long tenantId, Long agentId, Long agentVersionId, List<ToolConfig> tools) {
        if (agentToolBindingService == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent tool binding service is not available");
        }
        Map<Long, AgentToolBinding> existingBindings = agentToolBindingService.listVersionBindings(agentVersionId)
                .stream()
                .collect(Collectors.toMap(AgentToolBinding::getToolId, Function.identity(), (left, right) -> left));
        Set<Long> targetToolIds = tools.stream().map(ToolConfig::getId).collect(Collectors.toSet());
        for (ToolConfig tool : tools) {
            AgentToolBinding binding = existingBindings.get(tool.getId());
            if (binding == null) {
                binding = new AgentToolBinding();
                binding.setTenantId(tenantId);
                binding.setAgentId(agentId);
                binding.setAgentVersionId(agentVersionId);
                binding.setToolId(tool.getId());
            }
            binding.setBindingStatus("active");
            binding.setPolicyJson("{\"source\":\"agent_version\"}");
            agentToolBindingService.saveOrUpdate(binding);
        }
        for (AgentToolBinding binding : existingBindings.values()) {
            if (targetToolIds.contains(binding.getToolId())) {
                continue;
            }
            binding.setBindingStatus("inactive");
            binding.setPolicyJson("{\"source\":\"agent_version\"}");
            agentToolBindingService.saveOrUpdate(binding);
        }
    }

    private String defaultJson(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
