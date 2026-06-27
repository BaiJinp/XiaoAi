package com.xiaoai.agent.mcp;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * MCP 服务器配置实体
 */
@Getter
@Setter
@TableName("mcp_server")
public class McpServer extends TenantEntity {

    /**
     * 服务器代码（唯一标识）
     */
    private String serverCode;

    /**
     * 服务器名称
     */
    private String serverName;

    /**
     * 服务器类型（stdio、http、sse）
     */
    private String serverType;

    /**
     * 服务器URL（http/sse类型）
     */
    private String serverUrl;

    /**
     * 命令（stdio类型）
     */
    private String command;

    /**
     * 命令参数（JSON数组）
     */
    private String argsJson;

    /**
     * 环境变量（JSON对象）
     */
    private String envJson;

    /**
     * 状态（active、inactive、error）
     */
    private String status;

    /**
     * 最后错误信息
     */
    private String lastError;

    /**
     * 工具数量
     */
    private Integer toolCount;
}
