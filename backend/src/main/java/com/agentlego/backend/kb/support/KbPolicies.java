package com.agentlego.backend.kb.support;

import cn.hutool.core.util.StrUtil;
import com.agentlego.backend.common.JsonMaps;

import java.util.*;

/**
 * 智能体侧 {@code knowledge_base_policy} 解析。
 */
public final class KbPolicies {

    public static final String KEY_COLLECTION_IDS = "collectionIds";
    public static final String KEY_TOP_K = "topK";
    public static final String KEY_SCORE_THRESHOLD = "scoreThreshold";
    public static final String KEY_EMBEDDING_MODEL_ID = "embeddingModelId";
    /**
     * 是否启用全文检索通道并与向量做混合召回（RRF）；未设置时沿用应用配置默认值。
     */
    public static final String KEY_FULLTEXT_ENABLED = "fullTextEnabled";

    private KbPolicies() {
    }

    @SuppressWarnings("unchecked")
    public static List<String> collectionIds(Map<String, Object> policy) {
        if (policy == null || policy.isEmpty()) {
            return List.of();
        }
        Object v = policy.get(KEY_COLLECTION_IDS);
        if (!(v instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (Object o : list) {
            String s = StrUtil.trimToEmpty(StrUtil.toString(o));
            if (!s.isEmpty()) {
                ordered.add(s);
            }
        }
        return List.copyOf(ordered);
    }

    public static int topK(Map<String, Object> policy, int defaultTopK) {
        return JsonMaps.getInt(policy == null ? Map.of() : policy, KEY_TOP_K, defaultTopK);
    }

    public static double scoreThreshold(Map<String, Object> policy, double defaultThreshold) {
        return JsonMaps.getDouble(policy == null ? Map.of() : policy, KEY_SCORE_THRESHOLD, defaultThreshold);
    }

    public static String embeddingModelOverride(Map<String, Object> policy) {
        return JsonMaps.getString(policy == null ? Map.of() : policy, KEY_EMBEDDING_MODEL_ID, "").trim();
    }

    /**
     * 是否启用 KB 全文检索分支；策略中显式设置时优先生效，否则使用应用层默认值（如 {@code agentlego.kb.retrieve.fulltext-enabled}）。
     */
    public static boolean fullTextEnabled(Map<String, Object> policy, boolean defaultWhenAbsent) {
        Boolean b = JsonMaps.getBooleanOpt(policy == null ? Map.of() : policy, KEY_FULLTEXT_ENABLED);
        return b != null ? b : defaultWhenAbsent;
    }

    /**
     * 从策略中移除指定集合 id；若移除后不再有任何集合，返回空 Map（等价于关闭知识库 RAG）。
     */
    public static Map<String, Object> withoutCollectionId(Map<String, Object> policy, String collectionId) {
        if (policy == null || policy.isEmpty()) {
            return Map.of();
        }
        String cid = StrUtil.trimToEmpty(collectionId);
        if (cid.isEmpty()) {
            return new LinkedHashMap<>(policy);
        }
        List<String> ids = new ArrayList<>(collectionIds(policy));
        if (ids.isEmpty()) {
            return new LinkedHashMap<>(policy);
        }
        boolean removed = ids.removeIf(cid::equals);
        if (!removed) {
            return new LinkedHashMap<>(policy);
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>(policy);
        out.put(KEY_COLLECTION_IDS, List.copyOf(ids));
        return out;
    }
}
