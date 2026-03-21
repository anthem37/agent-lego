package com.agentlego.backend.kb.domain;

import java.util.List;
import java.util.Optional;

public interface KbCollectionRepository {

    String save(KbCollectionAggregate aggregate);

    Optional<KbCollectionAggregate> findById(String id);

    List<KbCollectionAggregate> listAll();

    List<KbCollectionAggregate> findByIds(List<String> ids);

    void deleteById(String id);
}
