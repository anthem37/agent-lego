package com.agentlego.backend.memory.application;

import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.memory.application.assembler.MemoryAssembler;
import com.agentlego.backend.memory.application.dto.CreateMemoryItemRequest;
import com.agentlego.backend.memory.application.dto.MemoryQueryRequest;
import com.agentlego.backend.memory.application.dto.MemoryQueryResponse;
import com.agentlego.backend.memory.domain.MemoryItemAggregate;
import com.agentlego.backend.memory.domain.MemoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * 记忆应用服务（Application Service）。
 * <p>
 * 职责：
 * - 写入记忆条目（content + metadata）；
 * - 查询记忆（当前为最小可用实现，后续可替换为向量检索/多路召回）。
 */
@Service
public class MemoryApplicationService {

    private final MemoryRepository repository;

    public MemoryApplicationService(MemoryRepository repository) {
        this.repository = repository;
    }

    public String createItem(CreateMemoryItemRequest req) {
        String content = ApiRequires.nonBlank(req.getContent(), "content");

        MemoryItemAggregate agg = new MemoryItemAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        agg.setOwnerScope(req.getOwnerScope());
        agg.setContent(content);
        agg.setMetadata(req.getMetadata() == null ? Map.of() : req.getMetadata());
        agg.setCreatedAt(Instant.now());

        return repository.save(agg);
    }

    public MemoryQueryResponse query(MemoryQueryRequest req) {
        java.util.List<MemoryItemAggregate> items = repository.search(req.getOwnerScope(), req.getQueryText(), req.getTopK());
        MemoryQueryResponse resp = new MemoryQueryResponse();
        resp.setItems(items.stream().map(MemoryAssembler::toDto).toList());
        return resp;
    }
}
