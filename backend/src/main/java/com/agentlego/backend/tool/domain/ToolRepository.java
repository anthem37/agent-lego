package com.agentlego.backend.tool.domain;

import java.util.List;
import java.util.Optional;

public interface ToolRepository {
    String save(ToolAggregate aggregate);

    void update(ToolAggregate aggregate);

    int deleteById(String id);

    /**
     * 全平台工具名是否与已有记录冲突（大小写不敏感）。更新时传入 {@code excludeId} 排除自身。
     */
    boolean existsOtherWithNameIgnoreCase(String name, String excludeId);

    Optional<ToolAggregate> findById(String id);

    List<ToolAggregate> findAll();

    long countByQuery(String q, String toolType);

    List<ToolAggregate> findPageByQuery(String q, String toolType, long offset, int limit);
}

