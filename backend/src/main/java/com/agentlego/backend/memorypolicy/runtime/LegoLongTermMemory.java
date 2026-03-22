package com.agentlego.backend.memorypolicy.runtime;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.memorypolicy.domain.MemoryItemRepository;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;
import com.agentlego.backend.memorypolicy.support.MemoryPolicySemantic;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;

/**
 * 按记忆策略执行：检索模式由 {@code retrievalMode} 决定（向量未接好时降级为关键词）；写入由 {@code writeMode} 决定。
 */
public class LegoLongTermMemory implements LongTermMemory {

    private static final Logger log = LoggerFactory.getLogger(LegoLongTermMemory.class);

    private final MemoryItemRepository memoryItemRepository;
    private final String policyId;
    private final String ownerScope;
    private final String strategyKind;
    private final int topK;
    private final String retrievalMode;
    private final String writeMode;
    private final String writeBackOnDuplicate;
    private final String agentId;

    public LegoLongTermMemory(
            MemoryItemRepository memoryItemRepository,
            String policyId,
            String ownerScope,
            String strategyKind,
            int topK,
            String retrievalMode,
            String writeMode,
            String writeBackOnDuplicate,
            String agentId
    ) {
        this.memoryItemRepository = Objects.requireNonNull(memoryItemRepository, "memoryItemRepository");
        this.policyId = Objects.requireNonNull(policyId, "policyId");
        this.ownerScope = Objects.requireNonNull(ownerScope, "ownerScope");
        this.strategyKind = strategyKind == null || strategyKind.isBlank()
                ? MemoryPolicySemantic.STRATEGY_EPISODIC_DIALOGUE
                : strategyKind.trim();
        this.topK = topK;
        this.retrievalMode = retrievalMode == null || retrievalMode.isBlank()
                ? MemoryPolicySemantic.RETRIEVAL_KEYWORD
                : retrievalMode.trim();
        this.writeMode = writeMode == null || writeMode.isBlank()
                ? MemoryPolicySemantic.WRITE_OFF
                : writeMode.trim();
        this.writeBackOnDuplicate = writeBackOnDuplicate == null ? "skip" : writeBackOnDuplicate;
        this.agentId = agentId;
    }

    @Override
    public Mono<String> retrieve(Msg msg) {
        return Mono.fromCallable(() -> {
                    if (!MemoryPolicySemantic.RETRIEVAL_KEYWORD.equalsIgnoreCase(retrievalMode)) {
                        log.warn(
                                "memory policy {} retrievalMode={} not fully implemented; falling back to KEYWORD",
                                policyId,
                                retrievalMode
                        );
                    }
                    String q = msg == null ? "" : msg.getTextContent();
                    if (q == null) {
                        q = "";
                    }
                    q = q.trim();
                    List<MemoryItemDO> rows = memoryItemRepository.searchByKeyword(policyId, q, topK);
                    if (rows.isEmpty()) {
                        return "";
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("[记忆：").append(strategyKind).append(" / ").append(ownerScope).append("] ");
                    sb.append("（检索=").append(retrievalMode).append("）");
                    sb.append("与当前输入相关的条目：\n");
                    int i = 1;
                    for (MemoryItemDO row : rows) {
                        sb.append(i++).append(". ");
                        String c = row.getContent() == null ? "" : row.getContent();
                        sb.append(c.replace("\r\n", "\n").trim());
                        sb.append("\n");
                    }
                    return sb.toString().trim();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> record(List<Msg> msgs) {
        if (!MemoryPolicySemantic.isWriteEnabled(writeMode) || msgs == null || msgs.isEmpty()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
                    List<String> assistantTexts = new ArrayList<>();
                    for (Msg m : msgs) {
                        if (m == null || m.getRole() != MsgRole.ASSISTANT) {
                            continue;
                        }
                        String t = m.getTextContent();
                        if (t != null && !t.isBlank()) {
                            assistantTexts.add(t.trim());
                        }
                    }
                    for (String text : assistantTexts) {
                        persistAssistantSnippet(text);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void persistAssistantSnippet(String text) {
        if (MemoryPolicySemantic.WRITE_ASSISTANT_SUMMARY.equalsIgnoreCase(writeMode)) {
            log.warn(
                    "ASSISTANT_SUMMARY not implemented; persisting raw assistant text for policy {}",
                    policyId
            );
        }
        if ("upsert".equalsIgnoreCase(writeBackOnDuplicate)) {
            String existing = memoryItemRepository.findIdByPolicyIdAndContent(policyId, text);
            if (existing != null) {
                memoryItemRepository.touchUpdatedAt(existing);
                return;
            }
        } else {
            String existing = memoryItemRepository.findIdByPolicyIdAndContent(policyId, text);
            if (existing != null) {
                return;
            }
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", "writeBack");
        meta.put("writeMode", writeMode);
        meta.put("policyId", policyId);
        if (agentId != null && !agentId.isBlank()) {
            meta.put("agentId", agentId);
        }
        MemoryItemDO row = new MemoryItemDO();
        row.setId(SnowflakeIdGenerator.nextId());
        row.setPolicyId(policyId);
        row.setContent(text);
        row.setMetadataJson(JsonMaps.toJson(meta));
        row.setCreatedAt(Instant.now());
        row.setUpdatedAt(null);
        memoryItemRepository.insert(row);
    }
}
