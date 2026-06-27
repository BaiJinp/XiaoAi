package com.xiaoai.agent.context.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.context.entity.ContextFile;
import com.xiaoai.agent.context.service.ContextFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 上下文文件控制器
 */
@RestController
@RequestMapping("/api/v1/context-files")
@CrossOrigin(origins = "*")
public class ContextFileController {

    private final ContextFileService contextFileService;

    @Autowired
    public ContextFileController(ContextFileService contextFileService) {
        this.contextFileService = contextFileService;
    }

    /**
     * 创建上下文文件
     */
    @PostMapping
    public ApiResponse<ContextFile> createContextFile(@RequestBody Map<String, Object> request) {
        Long tenantId = Long.valueOf(request.get("tenantId").toString());
        String code = (String) request.get("fileCode");
        String name = (String) request.get("fileName");
        String description = (String) request.get("description");
        String content = (String) request.get("content");
        String type = (String) request.get("fileType");
        Long projectId = request.get("projectId") != null ? Long.valueOf(request.get("projectId").toString()) : null;
        Long agentId = request.get("agentId") != null ? Long.valueOf(request.get("agentId").toString()) : null;
        Long userId = Long.valueOf(request.get("userId").toString());

        ContextFile contextFile = contextFileService.createContextFile(
                tenantId, code, name, description, content, type, projectId, agentId, userId);
        return ApiResponse.success(contextFile);
    }

    /**
     * 根据代码获取上下文文件
     */
    @GetMapping("/{code}")
    public ApiResponse<ContextFile> getByCode(@RequestParam Long tenantId, @PathVariable String code) {
        ContextFile contextFile = contextFileService.getByCode(tenantId, code);
        if (contextFile == null) {
            return ApiResponse.error("Context file not found");
        }
        return ApiResponse.success(contextFile);
    }

    /**
     * 获取项目的上下文文件
     */
    @GetMapping("/project/{projectId}")
    public ApiResponse<List<ContextFile>> getProjectContextFiles(@RequestParam Long tenantId,
                                                                  @PathVariable Long projectId) {
        List<ContextFile> files = contextFileService.getProjectContextFiles(tenantId, projectId);
        return ApiResponse.success(files);
    }

    /**
     * 获取 Agent 的上下文文件
     */
    @GetMapping("/agent/{agentId}")
    public ApiResponse<List<ContextFile>> getAgentContextFiles(@RequestParam Long tenantId,
                                                                @PathVariable Long agentId) {
        List<ContextFile> files = contextFileService.getAgentContextFiles(tenantId, agentId);
        return ApiResponse.success(files);
    }

    /**
     * 获取自动加载的上下文文件
     */
    @GetMapping("/auto-load")
    public ApiResponse<List<ContextFile>> getAutoLoadFiles(@RequestParam Long tenantId,
                                                            @RequestParam(required = false) Long projectId,
                                                            @RequestParam(required = false) Long agentId) {
        List<ContextFile> files = contextFileService.getAutoLoadFiles(tenantId, projectId, agentId);
        return ApiResponse.success(files);
    }

    /**
     * 搜索上下文文件
     */
    @GetMapping("/search")
    public ApiResponse<List<ContextFile>> searchContextFiles(@RequestParam Long tenantId,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) String fileType,
                                                              @RequestParam(defaultValue = "20") int limit) {
        List<ContextFile> files = contextFileService.searchContextFiles(tenantId, keyword, fileType, limit);
        return ApiResponse.success(files);
    }

    /**
     * 从 Markdown 导入
     */
    @PostMapping("/import")
    public ApiResponse<ContextFile> importFromMarkdown(@RequestParam Long tenantId,
                                                        @RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) Long agentId,
                                                        @RequestParam Long userId,
                                                        @RequestBody String markdown) {
        ContextFile contextFile = contextFileService.importFromMarkdown(tenantId, markdown, projectId, agentId, userId);
        return ApiResponse.success(contextFile);
    }

    /**
     * 导出为 Markdown
     */
    @GetMapping("/{id}/export")
    public ApiResponse<String> exportToMarkdown(@PathVariable Long id) {
        String markdown = contextFileService.exportToMarkdown(id);
        return ApiResponse.success(markdown);
    }

    /**
     * 设置自动加载
     */
    @PostMapping("/{id}/auto-load")
    public ApiResponse<String> setAutoLoad(@PathVariable Long id, @RequestParam boolean autoLoad) {
        contextFileService.setAutoLoad(id, autoLoad);
        return ApiResponse.success("Auto load setting updated");
    }

    /**
     * 更新优先级
     */
    @PostMapping("/{id}/priority")
    public ApiResponse<String> updatePriority(@PathVariable Long id, @RequestParam int priority) {
        contextFileService.updatePriority(id, priority);
        return ApiResponse.success("Priority updated");
    }
}
