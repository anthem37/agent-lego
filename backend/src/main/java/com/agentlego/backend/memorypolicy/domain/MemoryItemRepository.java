package com.agentlego.backend.memorypolicy.domain;

import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;

import java.util.List;

public interface MemoryItemRepository {

    /**
     * @param memoryNamespace 非空时仅返回 metadata.memoryNamespace 与之相等的条目；null/空 表示不按命名空间过滤（兼容历史数据）
     * @param strategyKind    非空时排除 metadata.strategyKind 与策略不一致的条目（null/空 metadata 视为兼容旧数据）
     * @param orderByTrgm     为 true 且 query 非空时，按 PostgreSQL {@code word_similarity(q, content)} 降序（需 pg_trgm）；否则按更新时间/创建时间
     */
    List<MemoryItemDO> searchByKeyword(
            String policyId,
            String queryText,
            int limit,
            String memoryNamespace,
            String strategyKind,
            boolean orderByTrgm
    );

    /**
     * 按 id 批量加载（顺序不保证与入参一致）。
     */
    List<MemoryItemDO> findByIds(String policyId, List<String> ids);

    /**
     * 策略下全部条目（用于重索引或删策略前清理向量）。
     */
    List<MemoryItemDO> listByPolicyId(String policyId);

    void insert(MemoryItemDO row);

    /**
     * @param memoryNamespace 非空时与写入侧一致；null/空 表示匹配未设置或空命名空间的条目
     * @param strategyKind    与 {@link #searchByKeyword} 一致
     */
    String findIdByPolicyIdAndContent(String policyId, String content, String memoryNamespace, String strategyKind);

    void touchUpdatedAt(String id);

    int deleteByPolicyIdAndItemId(String policyId, String itemId);
}
