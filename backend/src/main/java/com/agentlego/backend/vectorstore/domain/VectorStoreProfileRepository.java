package com.agentlego.backend.vectorstore.domain;

import java.util.List;
import java.util.Optional;

public interface VectorStoreProfileRepository {

    String save(VectorStoreProfileAggregate aggregate);

    Optional<VectorStoreProfileAggregate> findById(String id);

    List<VectorStoreProfileAggregate> listAll();

    int update(VectorStoreProfileAggregate aggregate);

    int deleteById(String id);
}
