package com.agentlego.backend.memory.domain;

import java.util.List;

public interface MemoryRepository {
    String save(MemoryItemAggregate aggregate);

    List<MemoryItemAggregate> search(String ownerScope, String queryText, int topK);
}

