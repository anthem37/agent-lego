package com.agentlego.backend.model.infrastructure;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.model.infrastructure.persistence.ModelDO;
import com.agentlego.backend.model.infrastructure.persistence.ModelMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 模型仓库实现（Repository Impl）。
 * <p>
 * 说明：configJson 统一通过 `JsonMaps` 做序列化/反序列化，减少重复样板代码。
 */
@Repository
public class ModelRepositoryImpl implements ModelRepository {
    private final ModelMapper mapper;

    public ModelRepositoryImpl(ModelMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String save(ModelAggregate aggregate) {
        ModelDO modelDO = new ModelDO();
        modelDO.setId(aggregate.getId());
        modelDO.setProvider(aggregate.getProvider());
        modelDO.setModelKey(aggregate.getModelKey());
        modelDO.setApiKeyCipher(aggregate.getApiKeyCipher());
        modelDO.setBaseUrl(aggregate.getBaseUrl());
        modelDO.setConfigJson(JsonMaps.toJson(aggregate.getConfig()));
        mapper.insert(modelDO);
        return aggregate.getId();
    }

    @Override
    public Optional<ModelAggregate> findById(String id) {
        ModelDO modelDO = mapper.findById(id);
        if (modelDO == null) {
            return Optional.empty();
        }
        ModelAggregate aggregate = new ModelAggregate();
        aggregate.setId(modelDO.getId());
        aggregate.setProvider(modelDO.getProvider());
        aggregate.setModelKey(modelDO.getModelKey());
        aggregate.setApiKeyCipher(modelDO.getApiKeyCipher());
        aggregate.setBaseUrl(modelDO.getBaseUrl());
        aggregate.setConfig(JsonMaps.parseObject(modelDO.getConfigJson()));
        aggregate.setCreatedAt(modelDO.getCreatedAt());
        return Optional.of(aggregate);
    }
}

