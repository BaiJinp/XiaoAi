package com.xiaoai.agent.memory.enhanced;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.memory.mapper.AgentMemoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 增强记忆搜索器
 * <p>
 * 为Agent记忆系统提供多种搜索策略，包括：
 * <ul>
 *   <li>fullTextSearch - 基于LIKE的关键词搜索（当前简化实现，生产环境应使用PostgreSQL FTS）</li>
 *   <li>vectorSearch - 向量相似度搜索（当前降级为全文搜索，生产环境需集成Milvus/pgvector等向量数据库）</li>
 *   <li>hybridSearch - 混合搜索，合并全文和向量搜索结果并去重</li>
 *   <li>advancedSearch - 支持agentId、userId、memoryType、memoryScope、置信度、时间范围等多维度过滤</li>
 *   <li>exportMemories - 将记忆导出为JSON/CSV/纯文本格式</li>
 * </ul>
 * 所有搜索仅返回status=confirmed的记忆（经过LLM确认的高质量记忆）。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Component
public class EnhancedMemorySearcher {

    private static final Logger log = LoggerFactory.getLogger(EnhancedMemorySearcher.class);

    private final AgentMemoryMapper memoryMapper;

    /**
     * 构造函数
     *
     * @param memoryMapper AgentMemory数据访问层，用于直接查询数据库
     */
    @Autowired
    public EnhancedMemorySearcher(AgentMemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    /**
     * 全文搜索记忆
     * <p>
     * 在summaryText和sourceText两个字段中进行关键词模糊匹配（LIKE查询）。
     * 仅返回status=confirmed的记忆。支持按agentId和userId过滤。
     * 按创建时间倒序排列，限制返回数量为limit条。
     * 查询异常时记录错误日志并返回空列表，不向外抛出异常。
     * </p>
     *
     * @param tenantId 租户ID（必填）
     * @param agentId  Agent ID（可选，null时不过滤）
     * @param userId   用户ID（可选，null时不过滤）
     * @param query    搜索关键词，为空时直接返回空列表
     * @param limit    最大返回数量
     * @return 匹配的记忆列表
     */
    public List<AgentMemory> fullTextSearch(Long tenantId, Long agentId, Long userId,
                                             String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        log.info("Full-text search memories: tenantId={}, query={}", tenantId, query);

        try {
            // 使用 PostgreSQL 全文搜索
            LambdaQueryWrapper<AgentMemory> wrapper = new LambdaQueryWrapper<AgentMemory>()
                    .eq(AgentMemory::getTenantId, tenantId)
                    .eq(AgentMemory::getStatus, "confirmed");

            if (agentId != null) {
                wrapper.eq(AgentMemory::getAgentId, agentId);
            }

            if (userId != null) {
                wrapper.eq(AgentMemory::getUserId, userId);
            }

            // 全文搜索（PostgreSQL FTS）
            // 注意：实际使用时需要使用 MyBatis-Plus 的自定义 SQL 或原生 SQL
            // 这里使用 LIKE 作为简化实现
            wrapper.and(w -> w
                    .like(AgentMemory::getSummaryText, query)
                    .or().like(AgentMemory::getSourceText, query)
            );

            wrapper.orderByDesc(AgentMemory::getCreatedAt)
                    .last("limit " + limit);

            List<AgentMemory> results = memoryMapper.selectList(wrapper);

            log.info("Full-text search completed: query={}, results={}", query, results.size());

            return results;

        } catch (Exception e) {
            log.error("Full-text search failed: query={}", query, e);
            return List.of();
        }
    }

    /**
     * 向量相似度搜索（当前为降级实现）
     * <p>
     * 生产环境需集成向量数据库（Milvus、Pinecone或PostgreSQL pgvector），
     * 将查询文本编码为向量后进行余弦相似度检索，返回相似度大于threshold的记忆。
     * 当前版本降级为调用fullTextSearch作为替代，threshold参数未实际使用。
     * </p>
     *
     * @param tenantId  租户ID
     * @param agentId   Agent ID（可选）
     * @param userId    用户ID（可选）
     * @param query     搜索文本，为空时返回空列表
     * @param limit     最大返回数量
     * @param threshold 最低相似度阈值（0~1），当前版本未使用
     * @return 相似记忆列表
     */
    public List<AgentMemory> vectorSearch(Long tenantId, Long agentId, Long userId,
                                          String query, int limit, double threshold) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        log.info("Vector search memories: tenantId={}, query={}, threshold={}",
                tenantId, query, threshold);

        // TODO: 集成向量数据库
        // 1. 将查询文本转换为向量
        // 2. 在向量数据库中搜索相似记忆
        // 3. 返回相似度大于阈值的记忆

        // 简化实现：使用全文搜索代替
        return fullTextSearch(tenantId, agentId, userId, query, limit);
    }

    /**
     * 混合搜索（全文 + 向量），合并两种搜索结果并去重。
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>分别对全文搜索和向量搜索各请求limit*2条结果（预留空间）</li>
     *   <li>以全文搜索结果为基础，按ID去重建立Map</li>
     *   <li>将向量搜索结果中未出现过的记忆追加进来</li>
     *   <li>截取前limit条返回</li>
     * </ol>
     * 当前由于向量搜索降级为全文搜索，实际效果等价于单一全文搜索结果。
     * </p>
     *
     * @param tenantId 租户ID
     * @param agentId  Agent ID（可选）
     * @param userId   用户ID（可选）
     * @param query    搜索关键词
     * @param limit    最终返回的最大数量
     * @return 合并去重后的记忆列表
     */
    public List<AgentMemory> hybridSearch(Long tenantId, Long agentId, Long userId,
                                          String query, int limit) {
        log.info("Hybrid search memories: tenantId={}, query={}", tenantId, query);

        // 1. 全文搜索
        List<AgentMemory> ftsResults = fullTextSearch(tenantId, agentId, userId, query, limit * 2);

        // 2. 向量搜索
        List<AgentMemory> vectorResults = vectorSearch(tenantId, agentId, userId, query, limit * 2, 0.7);

        // 3. 合并结果（去重）
        List<AgentMemory> combined = ftsResults.stream()
                .collect(Collectors.toMap(AgentMemory::getId, m -> m, (m1, m2) -> m1))
                .values()
                .stream()
                .collect(Collectors.toList());

        // 添加向量搜索结果中不在全文搜索结果中的记忆
        for (AgentMemory memory : vectorResults) {
            if (combined.stream().noneMatch(m -> m.getId().equals(memory.getId()))) {
                combined.add(memory);
            }
        }

        // 4. 限制返回数量
        return combined.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 高级多维度搜索
     * <p>
     * 支持以下过滤条件（均为可选）：
     * <ul>
     *   <li>agentId / userId - 精确匹配所属Agent或用户</li>
     *   <li>memoryType - 记忆类型（如fact、preference、procedure等）</li>
     *   <li>memoryScope - 记忆范围（如short_term、long_term等）</li>
     *   <li>minConfidence - 最低置信度过滤（字符串比较，如"high">="medium"）</li>
     *   <li>keyword - 在summaryText和sourceText中模糊匹配</li>
     *   <li>startDate / endDate - 创建时间范围过滤</li>
     *   <li>sortBy - 排序字段，支持"created_at"（默认）和"confidence"</li>
     *   <li>limit - 最大返回数量，默认20条</li>
     * </ul>
     * </p>
     *
     * @param tenantId 租户ID
     * @param query    高级搜索条件对象
     * @return 匹配的记忆列表
     */
    public List<AgentMemory> advancedSearch(Long tenantId, AdvancedSearchQuery query) {
        LambdaQueryWrapper<AgentMemory> wrapper = new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getTenantId, tenantId)
                .eq(AgentMemory::getStatus, "confirmed");

        // Agent 过滤
        if (query.getAgentId() != null) {
            wrapper.eq(AgentMemory::getAgentId, query.getAgentId());
        }

        // 用户过滤
        if (query.getUserId() != null) {
            wrapper.eq(AgentMemory::getUserId, query.getUserId());
        }

        // 类型过滤
        if (StringUtils.hasText(query.getMemoryType())) {
            wrapper.eq(AgentMemory::getMemoryType, query.getMemoryType());
        }

        // 范围过滤
        if (StringUtils.hasText(query.getMemoryScope())) {
            wrapper.eq(AgentMemory::getMemoryScope, query.getMemoryScope());
        }

        // 置信度过滤
        if (StringUtils.hasText(query.getMinConfidence())) {
            wrapper.ge(AgentMemory::getConfidence, query.getMinConfidence());
        }

        // 关键词搜索
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w
                    .like(AgentMemory::getSummaryText, query.getKeyword())
                    .or().like(AgentMemory::getSourceText, query.getKeyword())
            );
        }

        // 时间范围
        if (query.getStartDate() != null) {
            wrapper.ge(AgentMemory::getCreatedAt, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(AgentMemory::getCreatedAt, query.getEndDate());
        }

        // 排序
        if (StringUtils.hasText(query.getSortBy())) {
            switch (query.getSortBy()) {
                case "created_at":
                    wrapper.orderByDesc(AgentMemory::getCreatedAt);
                    break;
                case "confidence":
                    wrapper.orderByDesc(AgentMemory::getConfidence);
                    break;
                default:
                    wrapper.orderByDesc(AgentMemory::getCreatedAt);
            }
        } else {
            wrapper.orderByDesc(AgentMemory::getCreatedAt);
        }

        // 限制数量
        int limit = query.getLimit() != null ? query.getLimit() : 20;
        wrapper.last("limit " + limit);

        return memoryMapper.selectList(wrapper);
    }

    /**
     * 高级搜索查询参数对象
     * <p>
     * 封装advancedSearch方法的所有可选过滤和排序条件。
     * 所有字段均为可选，null或空字符串表示不过滤该维度。
     * </p>
     */
    public static class AdvancedSearchQuery {
        private Long agentId;
        private Long userId;
        private String memoryType;
        private String memoryScope;
        private String minConfidence;
        private String keyword;
        private java.time.OffsetDateTime startDate;
        private java.time.OffsetDateTime endDate;
        private String sortBy;
        private Integer limit;

        // Getters and Setters
public Long getAgentId() { return agentId; }
public void setAgentId(Long agentId) { this.agentId = agentId; }
public Long getUserId() { return userId; }
public void setUserId(Long userId) { this.userId = userId; }
public String getMemoryType() { return memoryType; }
public void setMemoryType(String memoryType) { this.memoryType = memoryType; }
public String getMemoryScope() { return memoryScope; }
public void setMemoryScope(String memoryScope) { this.memoryScope = memoryScope; }
public String getMinConfidence() { return minConfidence; }
public void setMinConfidence(String minConfidence) { this.minConfidence = minConfidence; }
public String getKeyword() { return keyword; }
public void setKeyword(String keyword) { this.keyword = keyword; }
        public java.time.OffsetDateTime getStartDate() { return startDate; }
public void setStartDate(java.time.OffsetDateTime startDate) { this.startDate = startDate; }
        public java.time.OffsetDateTime getEndDate() { return endDate; }
public void setEndDate(java.time.OffsetDateTime endDate) { this.endDate = endDate; }
public String getSortBy() { return sortBy; }
public void setSortBy(String sortBy) { this.sortBy = sortBy; }
public Integer getLimit() { return limit; }
public void setLimit(Integer limit) { this.limit = limit; }
    }

    /**
     * 导出记忆
     * <p>
     * 查询租户下所有status=confirmed的记忆（可按agentId和userId过滤），
     * 根据format参数选择导出格式：
     * <ul>
     *   <li>"json" - JSON数组格式，每条记录包含id、type、scope、summary、confidence</li>
     *   <li>"csv" - CSV格式，含表头行，字段为ID,Type,Scope,Summary,Confidence,CreatedAt</li>
     *   <li>其他 - 纯文本格式，每条记忆显示类型/范围/摘要/置信度/创建时间</li>
     * </ul>
     * </p>
     *
     * @param tenantId 租户ID（必填）
     * @param agentId  Agent ID（可选，null时不过滤）
     * @param userId   用户ID（可选，null时不过滤）
     * @param format   导出格式："json"、"csv"或其他（默认纯文本）
     * @return 格式化后的记忆字符串
     */
    public String exportMemories(Long tenantId, Long agentId, Long userId, String format) {
        List<AgentMemory> memories = memoryMapper.selectList(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getTenantId, tenantId)
                .eq(AgentMemory::getStatus, "confirmed")
                .eq(agentId != null, AgentMemory::getAgentId, agentId)
                .eq(userId != null, AgentMemory::getUserId, userId));

        if ("json".equalsIgnoreCase(format)) {
            return exportAsJson(memories);
        } else if ("csv".equalsIgnoreCase(format)) {
            return exportAsCsv(memories);
        } else {
            return exportAsText(memories);
        }
    }

    /**
     * 将记忆列表序列化为JSON数组字符串（手动拼接实现，生产环境应使用Jackson ObjectMapper）
     */
    private String exportAsJson(List<AgentMemory> memories) {
        // 简化实现，实际应该使用 Jackson
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < memories.size(); i++) {
            if (i > 0) json.append(",");
            AgentMemory m = memories.get(i);
            json.append(String.format(
                    "{\"id\":%d,\"type\":\"%s\",\"scope\":\"%s\",\"summary\":\"%s\",\"confidence\":\"%s\"}",
                    m.getId(), m.getMemoryType(), m.getMemoryScope(),
                    escapeJson(m.getSummaryText()), m.getConfidence()
            ));
        }
        json.append("]");
        return json.toString();
    }

    /**
     * 将记忆列表序列化为CSV格式字符串，含表头行（ID,Type,Scope,Summary,Confidence,CreatedAt）
     */
    private String exportAsCsv(List<AgentMemory> memories) {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Type,Scope,Summary,Confidence,CreatedAt\n");
        for (AgentMemory m : memories) {
            csv.append(String.format("%d,%s,%s,\"%s\",%s,%s\n",
                    m.getId(), m.getMemoryType(), m.getMemoryScope(),
                    escapeCsv(m.getSummaryText()), m.getConfidence(), m.getCreatedAt()
            ));
        }
        return csv.toString();
    }

    /**
     * 将记忆列表格式化为人类可读的纯文本（中文标注，每条记忆含类型/范围/摘要/置信度/创建时间）
     */
    private String exportAsText(List<AgentMemory> memories) {
        StringBuilder text = new StringBuilder();
        text.append("记忆导出\n");
        text.append("========\n\n");
        for (AgentMemory m : memories) {
            text.append(String.format("[%s/%s] %s (置信度: %s)\n",
                    m.getMemoryType(), m.getMemoryScope(), m.getSummaryText(), m.getConfidence()));
            text.append("创建时间: ").append(m.getCreatedAt()).append("\n\n");
        }
        return text.toString();
    }

    /**
     * JSON字符串转义：处理反斜杠、双引号、换行符、回车符和制表符，null时返回空串
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * CSV字段转义：将字段内的双引号替换为两个双引号（标准CSV转义规则），null时返回空串
     */
    private String escapeCsv(String text) {
        if (text == null) return "";
        return text.replace("\"", "\"\"");
    }
}
