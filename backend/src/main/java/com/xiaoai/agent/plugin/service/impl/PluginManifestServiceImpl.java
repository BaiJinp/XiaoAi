package com.xiaoai.agent.plugin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.agent.entity.AgentToolBinding;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.model.AgentVersionResponse;
import com.xiaoai.agent.agent.model.UpdateAgentConfigCommand;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.agent.service.AgentToolBindingService;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.plugin.entity.PluginManifest;
import com.xiaoai.agent.plugin.mapper.PluginManifestMapper;
import com.xiaoai.agent.plugin.model.ImportPluginManifestCommand;
import com.xiaoai.agent.plugin.model.PluginManifestResponse;
import com.xiaoai.agent.plugin.model.PluginToolResponse;
import com.xiaoai.agent.plugin.service.PluginManifestService;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Service
public class PluginManifestServiceImpl extends ServiceImpl<PluginManifestMapper, PluginManifest> implements PluginManifestService {

    private final ToolConfigService toolConfigService;
    private final AgentService agentService;
    private final AgentToolBindingService agentToolBindingService;
    private final AgentVersionService agentVersionService;
    private final ObjectMapper objectMapper;

    public PluginManifestServiceImpl(ToolConfigService toolConfigService,
                                     AgentService agentService,
                                     AgentToolBindingService agentToolBindingService,
                                     ObjectMapper objectMapper) {
        this(toolConfigService, agentService, agentToolBindingService, null, objectMapper);
    }

    @Autowired
    public PluginManifestServiceImpl(ToolConfigService toolConfigService,
                                     AgentService agentService,
                                     AgentToolBindingService agentToolBindingService,
                                     AgentVersionService agentVersionService,
                                     ObjectMapper objectMapper) {
        this.toolConfigService = toolConfigService;
        this.agentService = agentService;
        this.agentToolBindingService = agentToolBindingService;
        this.agentVersionService = agentVersionService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public PluginManifestResponse importManifest(ImportPluginManifestCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        JsonNode manifest = parseManifest(command.getManifestJson());
        String pluginCode = requiredText(manifest, "pluginCode");
        String pluginName = requiredText(manifest, "pluginName");
        String pluginVersion = textOrDefault(manifest, "pluginVersion", "0.0.1");
        String manifestHash = sha256Hex(command.getManifestJson());
        String auditedManifestJson = auditedManifestJson(manifest, manifestHash);
        JsonNode tools = manifest.path("tools");
        if (!tools.isArray() || tools.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Plugin manifest tools are required");
        }
        validateUniqueToolCodes(tools);

        PluginManifest plugin = findPlugin(tenantId, pluginCode, pluginVersion);
        boolean creating = plugin == null;
        if (plugin == null) {
            plugin = new PluginManifest();
            plugin.setTenantId(tenantId);
            plugin.setPluginCode(pluginCode);
        }
        plugin.setPluginName(pluginName);
        plugin.setPluginVersion(pluginVersion);
        plugin.setManifestJson(auditedManifestJson);
        plugin.setStatus("active");
        deactivateOtherPluginVersions(tenantId, pluginCode, pluginVersion);
        if (creating) {
            save(plugin);
        } else {
            updateById(plugin);
        }

        List<PluginToolResponse> toolResponses = new ArrayList<>();
        List<ToolConfig> importedTools = new ArrayList<>();
        for (JsonNode toolNode : tools) {
            ToolConfig tool = upsertTool(tenantId, toolNode, pluginCode, pluginVersion, manifestHash);
            importedTools.add(tool);
            AgentToolBinding binding = bindToolToAgentIfRequested(command, tenantId, pluginCode, pluginVersion, tool);
            toolResponses.add(binding == null
                    ? PluginToolResponse.from(tool)
                    : PluginToolResponse.from(tool, binding.getAgentId(), binding.getId()));
        }
        AgentVersionResponse versionResponse = appendToolsToDraftAgentVersionIfRequested(command, importedTools);
        return PluginManifestResponse.from(plugin, toolResponses,
                versionResponse == null ? null : versionResponse.getAgentVersionId(),
                versionResponse == null ? List.of() : versionResponse.getToolIds());
    }

    private AgentVersionResponse appendToolsToDraftAgentVersionIfRequested(ImportPluginManifestCommand command, List<ToolConfig> importedTools) {
        if (command.getAgentVersionId() == null) {
            return null;
        }
        if (command.getAgentId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent id is required when binding plugin tools to agent version");
        }
        if (agentVersionService == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent version service is not available");
        }
        AgentVersion version = agentVersionService.getVersion(command.getAgentVersionId());
        if (!command.getAgentId().equals(version.getAgentId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent version does not belong to agent");
        }
        if (!"draft".equals(version.getVersionStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only draft agent version can update tool scope");
        }
        LinkedHashSet<Long> toolIds = new LinkedHashSet<>(parseToolIds(version.getToolScopeJson()));
        for (ToolConfig tool : importedTools) {
            if (tool.getId() != null) {
                toolIds.add(tool.getId());
            }
        }
        UpdateAgentConfigCommand updateCommand = new UpdateAgentConfigCommand();
        updateCommand.setToolIds(new ArrayList<>(toolIds));
        return agentVersionService.replaceVersionTools(command.getAgentId(), command.getAgentVersionId(), updateCommand);
    }

    private List<Long> parseToolIds(String toolScopeJson) {
        if (!StringUtils.hasText(toolScopeJson)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(toolScopeJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<Long> toolIds = new ArrayList<>();
            for (JsonNode item : root) {
                JsonNode toolId = item.get("toolId");
                if (toolId != null && toolId.canConvertToLong()) {
                    toolIds.add(toolId.asLong());
                }
            }
            return toolIds;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void validateUniqueToolCodes(JsonNode tools) {
        Set<String> toolCodes = new HashSet<>();
        for (JsonNode toolNode : tools) {
            String toolCode = requiredText(toolNode, "toolCode");
            if (!toolCodes.add(toolCode)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Plugin manifest duplicate toolCode: " + toolCode);
            }
        }
    }

    @Override
public List<PluginManifestResponse> listManifests() {
        Long tenantId = UserContextHolder.requireTenantId();
        List<PluginManifest> plugins = list(new LambdaQueryWrapper<PluginManifest>()
                .eq(PluginManifest::getTenantId, tenantId)
                .orderByDesc(PluginManifest::getId));
        return plugins.stream()
                .map(plugin -> PluginManifestResponse.from(plugin, listPluginTools(plugin)))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public PluginManifestResponse enablePlugin(Long pluginId) {
        return updatePluginStatus(pluginId, "active");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public PluginManifestResponse disablePlugin(Long pluginId) {
        return updatePluginStatus(pluginId, "inactive");
    }

    private PluginManifestResponse updatePluginStatus(Long pluginId, String status) {
        PluginManifest plugin = getTenantPlugin(pluginId);
        if ("active".equals(status)) {
            deactivateOtherPluginVersions(plugin.getTenantId(), plugin.getPluginCode(), plugin.getPluginVersion());
        }
        plugin.setStatus(status);
        updateById(plugin);
        List<PluginToolResponse> tools = listPluginTools(plugin);
        for (PluginToolResponse toolResponse : tools) {
            ToolConfig tool = findTool(plugin.getTenantId(), toolResponse.getToolCode());
            if (tool != null) {
                tool.setStatus(resolveToolStatusForPluginStatus(plugin, toolResponse.getToolCode(), status));
                toolConfigService.saveOrUpdate(tool);
            }
        }
        return PluginManifestResponse.from(plugin, tools);
    }

    private String resolveToolStatusForPluginStatus(PluginManifest plugin, String toolCode, String pluginStatus) {
        if (!"inactive".equals(pluginStatus) || !isToolDeclaredByAnotherActiveVersion(plugin, toolCode)) {
            return pluginStatus;
        }
        return "active";
    }

    private boolean isToolDeclaredByAnotherActiveVersion(PluginManifest plugin, String toolCode) {
        if (!StringUtils.hasText(toolCode)) {
            return false;
        }
        List<PluginManifest> activeVersions = safeList(list(new LambdaQueryWrapper<PluginManifest>()
                .eq(PluginManifest::getTenantId, plugin.getTenantId())
                .eq(PluginManifest::getPluginCode, plugin.getPluginCode())
                .ne(PluginManifest::getId, plugin.getId())
                .eq(PluginManifest::getStatus, "active")));
        for (PluginManifest activeVersion : activeVersions) {
            if (manifestDeclaresTool(activeVersion.getManifestJson(), toolCode)) {
                return true;
            }
        }
        return false;
    }

    private boolean manifestDeclaresTool(String manifestJson, String toolCode) {
        try {
            JsonNode manifest = objectMapper.readTree(manifestJson);
            JsonNode tools = manifest.path("tools");
            if (!tools.isArray()) {
                return false;
            }
            for (JsonNode toolNode : tools) {
                if (toolCode.equals(textOrDefault(toolNode, "toolCode", null))) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private List<PluginToolResponse> listPluginTools(PluginManifest plugin) {
        try {
            JsonNode manifest = objectMapper.readTree(plugin.getManifestJson());
            JsonNode tools = manifest.path("tools");
            if (!tools.isArray()) {
                return List.of();
            }
            List<PluginToolResponse> responses = new ArrayList<>();
            for (JsonNode toolNode : tools) {
                ToolConfig tool = findTool(plugin.getTenantId(), textOrDefault(toolNode, "toolCode", ""));
                if (tool != null) {
                    responses.add(PluginToolResponse.from(tool));
                }
            }
            return responses;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ToolConfig upsertTool(Long tenantId,
                                  JsonNode toolNode,
                                  String pluginCode,
                                  String pluginVersion,
                                  String manifestHash) {
        String toolCode = requiredText(toolNode, "toolCode");
        String toolName = requiredText(toolNode, "toolName");
        String toolType = requiredText(toolNode, "toolType");
        String riskLevel = textOrDefault(toolNode, "riskLevel", "medium");
        String endpointUrl = textOrDefault(toolNode, "endpointUrl", null);
        String schemaJson = nodeJson(toolNode.path("schema"));
        String authConfigJson = auditedToolAuthConfigJson(toolNode.path("authConfig"), pluginCode, pluginVersion, manifestHash, toolType, endpointUrl);
        validateToolDefinition(toolCode, toolType, endpointUrl, schemaJson, authConfigJson);

        ToolConfig tool = findTool(tenantId, toolCode);
        if (tool == null) {
            tool = new ToolConfig();
            tool.setTenantId(tenantId);
            tool.setToolCode(toolCode);
        }
        tool.setToolName(toolName);
        tool.setToolType(toolType);
        tool.setRiskLevel(riskLevel);
        tool.setEndpointUrl(endpointUrl);
        tool.setSchemaJson(schemaJson);
        tool.setAuthConfigJson(authConfigJson);
        tool.setStatus("active");
        toolConfigService.saveOrUpdate(tool);
        if (tool.getId() == null) {
            ToolConfig persisted = findTool(tenantId, toolCode);
            if (persisted != null) {
                return persisted;
            }
        }
        return tool;
    }

    private AgentToolBinding bindToolToAgentIfRequested(ImportPluginManifestCommand command,
                                                        Long tenantId,
                                                        String pluginCode,
                                                        String pluginVersion,
                                                        ToolConfig tool) {
        if (command.getAgentId() == null) {
            return null;
        }
        agentService.getAgent(command.getAgentId());
        if (tool.getId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Plugin tool id is required before binding");
        }
        AgentToolBinding binding = agentToolBindingService.getOne(new LambdaQueryWrapper<AgentToolBinding>()
                .eq(AgentToolBinding::getTenantId, tenantId)
                .eq(AgentToolBinding::getAgentId, command.getAgentId())
                .eq(AgentToolBinding::getToolId, tool.getId())
                .last("limit 1"));
        if (binding == null) {
            binding = new AgentToolBinding();
            binding.setTenantId(tenantId);
            binding.setAgentId(command.getAgentId());
            binding.setToolId(tool.getId());
        }
        binding.setBindingStatus("active");
        binding.setPolicyJson(pluginBindingPolicy(pluginCode, pluginVersion));
        agentToolBindingService.saveOrUpdate(binding);
        return binding;
    }

    private String pluginBindingPolicy(String pluginCode, String pluginVersion) {
        ObjectNode policy = objectMapper.createObjectNode();
        policy.put("source", "plugin_manifest");
        policy.put("pluginCode", pluginCode);
        policy.put("pluginVersion", pluginVersion);
        return policy.toString();
    }

    private String auditedManifestJson(JsonNode manifest, String manifestHash) {
        ObjectNode manifestObject = manifest.deepCopy();
        ObjectNode audit = manifestObject.putObject("_audit");
        audit.put("sourceType", "plugin_manifest");
        audit.put("manifestHash", manifestHash);
        return manifestObject.toString();
    }

    private String auditedToolAuthConfigJson(JsonNode authConfig,
                                             String pluginCode,
                                             String pluginVersion,
                                             String manifestHash,
                                             String toolType,
                                             String endpointUrl) {
        ObjectNode authConfigObject = readObjectOrEmpty(authConfig);
        ObjectNode plugin = authConfigObject.putObject("_plugin");
        plugin.put("sourceType", "plugin_manifest");
        plugin.put("pluginCode", pluginCode);
        plugin.put("pluginVersion", pluginVersion);
        plugin.put("manifestHash", manifestHash);
        ObjectNode policy = authConfigObject.putObject("_policy");
        policy.put("sourceType", "plugin_manifest");
        policy.put("toolType", toolType);
        if ("cli".equals(toolType)) {
            policy.put("allowedExecutable", endpointUrl);
            String workingDirectory = authConfigObject.path("workingDirectory").asText(null);
            if (StringUtils.hasText(workingDirectory)) {
                policy.put("allowedWorkingDirectory", Path.of(workingDirectory).normalize().toString());
            }
        }
        if ("http".equals(toolType)) {
            URI endpoint;
            try {
                endpoint = URI.create(endpointUrl);
            } catch (Exception exception) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "HTTP endpointUrl is invalid");
            }
            if (!StringUtils.hasText(endpoint.getScheme()) || !StringUtils.hasText(endpoint.getHost())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "HTTP endpointUrl is invalid");
            }
            policy.put("allowedScheme", endpoint.getScheme());
            policy.put("allowedHost", endpoint.getHost());
            policy.put("allowPrivateNetwork", authConfigObject.path("allowPrivateNetwork").asBoolean(false));
        }
        return authConfigObject.toString();
    }

    private ObjectNode readObjectOrEmpty(JsonNode node) {
        if (node != null && !node.isMissingNode() && !node.isNull() && node.isObject()) {
            return node.deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Plugin manifest hash failed");
        }
    }

    private void validateToolDefinition(String toolCode,
                                        String toolType,
                                        String endpointUrl,
                                        String schemaJson,
                                        String authConfigJson) {
        if (!"cli".equals(toolType) && !"http".equals(toolType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only cli/http plugin tools are supported in MVP");
        }
        if ("cli".equals(toolType) && !toolCode.startsWith("controlled.cli.")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CLI toolCode must start with controlled.cli.");
        }
        if ("http".equals(toolType) && !toolCode.startsWith("controlled.http.")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "HTTP toolCode must start with controlled.http.");
        }
        if (!StringUtils.hasText(endpointUrl)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, toolType.toUpperCase() + " endpointUrl is required");
        }
        if (!StringUtils.hasText(schemaJson) || "{}".equals(schemaJson)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, toolType.toUpperCase() + " schema is required");
        }
        if ("http".equals(toolType)) {
            return;
        }
        validateWorkingDirectory(authConfigJson);
    }

    private void validateWorkingDirectory(String authConfigJson) {
        if (!StringUtils.hasText(authConfigJson) || "{}".equals(authConfigJson)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CLI workingDirectory is required");
        }
        try {
            JsonNode config = objectMapper.readTree(authConfigJson);
            String workingDirectory = config.path("workingDirectory").asText(null);
            if (!StringUtils.hasText(workingDirectory)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "CLI workingDirectory is required");
            }
            Path path = Path.of(workingDirectory).normalize();
            if (!path.isAbsolute() || !path.toFile().isDirectory()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "CLI workingDirectory is not available");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CLI authConfig is invalid");
        }
    }

    private PluginManifest findPlugin(Long tenantId, String pluginCode, String pluginVersion) {
        return getBaseMapper().selectOne(new LambdaQueryWrapper<PluginManifest>()
                .eq(PluginManifest::getTenantId, tenantId)
                .eq(PluginManifest::getPluginCode, pluginCode)
                .eq(PluginManifest::getPluginVersion, pluginVersion)
                .last("limit 1"));
    }

    private void deactivateOtherPluginVersions(Long tenantId, String pluginCode, String activeVersion) {
        List<PluginManifest> otherVersions = list(new LambdaQueryWrapper<PluginManifest>()
                .eq(PluginManifest::getTenantId, tenantId)
                .eq(PluginManifest::getPluginCode, pluginCode)
                .ne(PluginManifest::getPluginVersion, activeVersion)
                .eq(PluginManifest::getStatus, "active"));
        for (PluginManifest otherVersion : otherVersions) {
            otherVersion.setStatus("inactive");
            updateById(otherVersion);
        }
    }

    private PluginManifest getTenantPlugin(Long pluginId) {
        Long tenantId = UserContextHolder.requireTenantId();
        PluginManifest plugin = getBaseMapper().selectOne(new LambdaQueryWrapper<PluginManifest>()
                .eq(PluginManifest::getTenantId, tenantId)
                .eq(PluginManifest::getId, pluginId)
                .last("limit 1"));
        if (plugin == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Plugin manifest not found");
        }
        return plugin;
    }

    private ToolConfig findTool(Long tenantId, String toolCode) {
        return toolConfigService.getOne(new LambdaQueryWrapper<ToolConfig>()
                .eq(ToolConfig::getTenantId, tenantId)
                .eq(ToolConfig::getToolCode, toolCode)
                .last("limit 1"));
    }

    private JsonNode parseManifest(String manifestJson) {
        try {
            JsonNode manifest = objectMapper.readTree(manifestJson);
            if (manifest == null || !manifest.isObject()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Plugin manifest must be a JSON object");
            }
            return manifest;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Plugin manifest is invalid JSON");
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = textOrDefault(node, fieldName, null);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Plugin manifest field is required: " + fieldName);
        }
        return value;
    }

    private String textOrDefault(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node.path(fieldName);
        return value.isTextual() ? value.asText() : defaultValue;
    }

    private String nodeJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "{}";
        }
        return node.toString();
    }
}
