package com.agentlego.backend.kb.support;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.application.dto.KbMultiRetrievePreviewRequest;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbCollectionRepository;
import org.springframework.http.HttpStatus;

import java.util.*;

/**
 * 多集合召回预览：集合 ID 归一化、存在性校验、嵌入模型一致性（与智能体 {@code knowledge_base_policy} 约束对齐）。
 */
public final class KbMultiRetrievePreviewRules {

    private KbMultiRetrievePreviewRules() {
    }

    /**
     * 将请求中的 collectionIds 去重、trim，并校验数量上限。
     */
    public static List<String> normalizeCollectionIds(KbMultiRetrievePreviewRequest req) {
        if (req == null || req.getCollectionIds() == null || req.getCollectionIds().isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "collectionIds 不能为空", HttpStatus.BAD_REQUEST);
        }
        LinkedHashSet<String> idSet = new LinkedHashSet<>();
        for (String id : req.getCollectionIds()) {
            if (id != null && !id.isBlank()) {
                idSet.add(id.trim());
            }
        }
        if (idSet.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "collectionIds 不能为空", HttpStatus.BAD_REQUEST);
        }
        if (idSet.size() > KbLimits.MAX_MULTI_COLLECTION_RETRIEVE) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "collectionIds 数量过多（最多 " + KbLimits.MAX_MULTI_COLLECTION_RETRIEVE + " 个）",
                    HttpStatus.BAD_REQUEST
            );
        }
        return new ArrayList<>(idSet);
    }

    /**
     * 按 ID 批量加载集合；若数量不一致则存在未知 ID；并校验所有集合使用相同 {@code embeddingModelId}。
     */
    public static List<KbCollectionAggregate> loadAllSameEmbeddingOrThrow(
            KbCollectionRepository collectionRepository,
            List<String> ids
    ) {
        List<KbCollectionAggregate> cols = collectionRepository.findByIds(ids);
        if (cols.size() != ids.size()) {
            throw new ApiException("NOT_FOUND", "部分知识库集合未找到", HttpStatus.NOT_FOUND);
        }
        String expectedModel = cols.get(0).getEmbeddingModelId();
        for (KbCollectionAggregate c : cols) {
            if (!expectedModel.equals(c.getEmbeddingModelId())) {
                throw new ApiException(
                        "VALIDATION_ERROR",
                        "多集合召回要求所有集合使用相同的 embedding_model_id（与智能体 knowledge_base_policy 一致）",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
        return cols;
    }

    public static Map<String, KbCollectionAggregate> indexCollectionsById(List<KbCollectionAggregate> cols) {
        Map<String, KbCollectionAggregate> m = new LinkedHashMap<>();
        for (KbCollectionAggregate c : cols) {
            m.put(c.getId(), c);
        }
        return m;
    }
}
