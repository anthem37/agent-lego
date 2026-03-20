package com.agentlego.backend.model.domain;

import java.util.List;
import java.util.Optional;

public interface ModelRepository {
    String save(ModelAggregate aggregate);

    Optional<ModelAggregate> findById(String id);

    /**
     * 按创建时间倒序返回全部模型。
     */
    List<ModelAggregate> findAllOrderByCreatedAtDesc();

    /**
     * 按主键更新（全字段覆盖写入，除 id/provider/created_at）。
     */
    void update(ModelAggregate aggregate);

    /**
     * 按主键删除。
     *
     * @return 影响行数
     */
    int deleteById(String id);
}

