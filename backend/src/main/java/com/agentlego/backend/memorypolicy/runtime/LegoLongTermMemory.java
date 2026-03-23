package com.agentlego.backend.memorypolicy.runtime;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.memorypolicy.domain.MemoryItemRepository;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;
import com.agentlego.backend.memorypolicy.support.MemoryPolicySemantic;
import com.agentlego.backend.memorypolicy.support.MemoryRoughSummary;
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
 * 按记忆策略执行：KEYWORD / VECTOR / HYBRID（向量侧依赖外置向量库与 profile 配置）。
 */
public class LegoLongTermMemory implements LongTermMemory {

    private static final Logger log = LoggerFactory.getLogger(LegoLongTermMemory.class);

    private final MemoryItemRepository memoryItemRepository;
    private final MemoryPolicyDO policy;
    private final MemoryVectorIndexService memoryVectorIndexService;
    private final String agentId;
    /**
     * 非空时检索/去重限定在该命名空间；null 表示与历史「无命名空间」条目共用。
     */
    private final String memoryNamespace;
    /**
     * ASSISTANT_SUMMARY 时传给 {@link MemoryRoughSummary#summarize(String, int)} 的上限（已解析，含默认 480）。
     */
    private final int roughSummaryMaxChars;

    public LegoLongTermMemory(
            MemoryItemRepository memoryItemRepository,
            MemoryPolicyDO policy,
            MemoryVectorIndexService memoryVectorIndexService,
            String agentId,
            String memoryNamespace,
            int roughSummaryMaxChars
    ) {
        this.memoryItemRepository = Objects.requireNonNull(memoryItemRepository, "memoryItemRepository");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.memoryVectorIndexService = memoryVectorIndexService;
        this.agentId = agentId;
        this.memoryNamespace = memoryNamespace == null || memoryNamespace.isBlank()
                ? null
                : memoryNamespace.trim();
        this.roughSummaryMaxChars = roughSummaryMaxChars;
    }

    private static List<MemoryItemDO> mergeHybrid(List<MemoryItemDO> vec, List<MemoryItemDO> kw, int topK) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<MemoryItemDO> out = new ArrayList<>();
        for (MemoryItemDO r : vec) {
            if (seen.add(r.getId())) {
                out.add(r);
            }
            if (out.size() >= topK) {
                return out;
            }
        }
        for (MemoryItemDO r : kw) {
            if (seen.add(r.getId())) {
                out.add(r);
            }
            if (out.size() >= topK) {
                return out;
            }
        }
        return out;
    }

    private String policyId() {
        return policy.getId();
    }

    private String strategyKind() {
        String s = policy.getStrategyKind();
        return s == null || s.isBlank() ? MemoryPolicySemantic.STRATEGY_EPISODIC_DIALOGUE : s.trim();
    }

    private int topK() {
        return policy.getTopK() == null ? 5 : policy.getTopK();
    }

    private String retrievalMode() {
        String r = policy.getRetrievalMode();
        return r == null || r.isBlank() ? MemoryPolicySemantic.RETRIEVAL_KEYWORD : r.trim();
    }

    private String writeMode() {
        String w = policy.getWriteMode();
        return w == null || w.isBlank() ? MemoryPolicySemantic.WRITE_OFF : w.trim();
    }

    private String writeBackOnDuplicate() {
        String w = policy.getWriteBackOnDuplicate();
        return w == null ? "skip" : w;
    }

    @Override
    public Mono<String> retrieve(Msg msg) {
        return Mono.fromCallable(() -> {
                    String q = msg == null ? "" : msg.getTextContent();
                    if (q == null) {
                        q = "";
                    }
                    q = q.trim();
                    boolean qNonEmpty = !q.isEmpty();
                    List<MemoryItemDO> rows = retrieveRows(q, qNonEmpty);
                    if (rows.isEmpty()) {
                        return "";
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("[记忆：").append(strategyKind()).append(" / ").append(policy.getOwnerScope()).append("] ");
                    sb.append("（检索=").append(retrievalMode()).append("）");
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

    private List<MemoryItemDO> retrieveRows(String q, boolean qNonEmpty) {
        String mode = retrievalMode();
        boolean vectorReady = memoryVectorIndexService != null
                && memoryVectorIndexService.resolveAggregate(policy).isPresent();

        if (MemoryPolicySemantic.RETRIEVAL_KEYWORD.equalsIgnoreCase(mode) || !vectorReady) {
            if (!vectorReady && MemoryPolicySemantic.isVectorRetrieval(mode)) {
                log.warn(
                        "memory policy {} retrievalMode={} but vector link not configured; using KEYWORD",
                        policyId(),
                        mode
                );
            }
            return memoryItemRepository.searchByKeyword(
                    policyId(),
                    q,
                    topK(),
                    memoryNamespace,
                    strategyKind(),
                    qNonEmpty
            );
        }
        if (MemoryPolicySemantic.RETRIEVAL_VECTOR.equalsIgnoreCase(mode)) {
            if (!qNonEmpty) {
                return memoryItemRepository.searchByKeyword(
                        policyId(),
                        "",
                        topK(),
                        memoryNamespace,
                        strategyKind(),
                        false
                );
            }
            return memoryVectorIndexService.searchByVector(policy, q, topK(), memoryNamespace, strategyKind());
        }
        if (MemoryPolicySemantic.RETRIEVAL_HYBRID.equalsIgnoreCase(mode)) {
            if (!qNonEmpty) {
                return memoryItemRepository.searchByKeyword(
                        policyId(),
                        "",
                        topK(),
                        memoryNamespace,
                        strategyKind(),
                        false
                );
            }
            List<MemoryItemDO> vec = memoryVectorIndexService.searchByVector(
                    policy,
                    q,
                    topK(),
                    memoryNamespace,
                    strategyKind()
            );
            List<MemoryItemDO> kw = memoryItemRepository.searchByKeyword(
                    policyId(),
                    q,
                    topK() * 2,
                    memoryNamespace,
                    strategyKind(),
                    true
            );
            return mergeHybrid(vec, kw, topK());
        }
        return memoryItemRepository.searchByKeyword(
                policyId(),
                q,
                topK(),
                memoryNamespace,
                strategyKind(),
                qNonEmpty
        );
    }

    @Override
    public Mono<Void> record(List<Msg> msgs) {
        if (!MemoryPolicySemantic.isWriteEnabled(writeMode()) || msgs == null || msgs.isEmpty()) {
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
        String toStore = text;
        if (MemoryPolicySemantic.WRITE_ASSISTANT_SUMMARY.equalsIgnoreCase(writeMode())) {
            toStore = MemoryRoughSummary.summarize(text, roughSummaryMaxChars);
        }
        if ("upsert".equalsIgnoreCase(writeBackOnDuplicate())) {
            String existing = memoryItemRepository.findIdByPolicyIdAndContent(
                    policyId(),
                    toStore,
                    memoryNamespace,
                    strategyKind()
            );
            if (existing != null) {
                memoryItemRepository.touchUpdatedAt(existing);
                return;
            }
        } else {
            String existing = memoryItemRepository.findIdByPolicyIdAndContent(
                    policyId(),
                    toStore,
                    memoryNamespace,
                    strategyKind()
            );
            if (existing != null) {
                return;
            }
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", "writeBack");
        meta.put("writeMode", writeMode());
        meta.put("policyId", policyId());
        meta.put("strategyKind", strategyKind());
        if (MemoryPolicySemantic.WRITE_ASSISTANT_SUMMARY.equalsIgnoreCase(writeMode())) {
            meta.put("summaryKind", "ROUGH_CHAR_CAP");
            meta.put("roughSummaryMaxChars", roughSummaryMaxChars);
            meta.put("originalCharLength", text.length());
            if (!text.equals(toStore)) {
                meta.put("truncated", true);
            }
        }
        if (agentId != null && !agentId.isBlank()) {
            meta.put("agentId", agentId);
        }
        if (memoryNamespace != null) {
            meta.put("memoryNamespace", memoryNamespace);
        }
        MemoryItemDO row = new MemoryItemDO();
        row.setId(SnowflakeIdGenerator.nextId());
        row.setPolicyId(policyId());
        row.setContent(toStore);
        row.setMetadataJson(JsonMaps.toJson(meta));
        row.setCreatedAt(Instant.now());
        row.setUpdatedAt(null);
        memoryItemRepository.insert(row);
        if (memoryVectorIndexService != null) {
            memoryVectorIndexService.indexMemoryItem(policy, row);
        }
    }
}
