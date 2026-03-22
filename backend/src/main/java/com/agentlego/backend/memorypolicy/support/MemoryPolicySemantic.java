package com.agentlego.backend.memorypolicy.support;

import com.agentlego.backend.api.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Set;

/**
 * 记忆策略语义常量与校验（与 {@code docs/memory-strategy.md} 一致）。
 */
public final class MemoryPolicySemantic {

    public static final String STRATEGY_EPISODIC_DIALOGUE = "EPISODIC_DIALOGUE";
    public static final String STRATEGY_USER_PROFILE = "USER_PROFILE";
    public static final String STRATEGY_TASK_CONTEXT = "TASK_CONTEXT";

    public static final String SCOPE_CUSTOM_NAMESPACE = "CUSTOM_NAMESPACE";
    public static final String SCOPE_TENANT = "TENANT";
    public static final String SCOPE_USER = "USER";
    public static final String SCOPE_AGENT = "AGENT";

    public static final String RETRIEVAL_KEYWORD = "KEYWORD";
    public static final String RETRIEVAL_VECTOR = "VECTOR";
    public static final String RETRIEVAL_HYBRID = "HYBRID";

    public static final String WRITE_OFF = "OFF";
    public static final String WRITE_ASSISTANT_RAW = "ASSISTANT_RAW";
    public static final String WRITE_ASSISTANT_SUMMARY = "ASSISTANT_SUMMARY";

    private static final Set<String> STRATEGIES = Set.of(
            STRATEGY_EPISODIC_DIALOGUE, STRATEGY_USER_PROFILE, STRATEGY_TASK_CONTEXT
    );
    private static final Set<String> SCOPES = Set.of(
            SCOPE_CUSTOM_NAMESPACE, SCOPE_TENANT, SCOPE_USER, SCOPE_AGENT
    );
    private static final Set<String> RETRIEVALS = Set.of(
            RETRIEVAL_KEYWORD, RETRIEVAL_VECTOR, RETRIEVAL_HYBRID
    );
    private static final Set<String> WRITES = Set.of(
            WRITE_OFF, WRITE_ASSISTANT_RAW, WRITE_ASSISTANT_SUMMARY
    );

    private MemoryPolicySemantic() {
    }

    public static String normalizeStrategyKind(String v) {
        if (v == null || v.isBlank()) {
            return STRATEGY_EPISODIC_DIALOGUE;
        }
        String s = v.trim().toUpperCase();
        require(STRATEGIES.contains(s), "INVALID_STRATEGY_KIND", "不支持的 strategyKind：" + v);
        return s;
    }

    public static String normalizeScopeKind(String v) {
        if (v == null || v.isBlank()) {
            return SCOPE_CUSTOM_NAMESPACE;
        }
        String s = v.trim().toUpperCase();
        require(SCOPES.contains(s), "INVALID_SCOPE_KIND", "不支持的 scopeKind：" + v);
        return s;
    }

    public static String normalizeRetrievalMode(String v) {
        if (v == null || v.isBlank()) {
            return RETRIEVAL_KEYWORD;
        }
        String s = v.trim().toUpperCase();
        require(RETRIEVALS.contains(s), "INVALID_RETRIEVAL_MODE", "不支持的 retrievalMode：" + v);
        return s;
    }

    public static String normalizeWriteMode(String v) {
        if (v == null || v.isBlank()) {
            return WRITE_OFF;
        }
        String s = v.trim().toUpperCase();
        require(WRITES.contains(s), "INVALID_WRITE_MODE", "不支持的 writeMode：" + v);
        return s;
    }

    public static boolean isWriteEnabled(String writeMode) {
        String m = writeMode == null ? WRITE_OFF : writeMode.trim().toUpperCase();
        return WRITE_ASSISTANT_RAW.equals(m) || WRITE_ASSISTANT_SUMMARY.equals(m);
    }

    private static void require(boolean ok, String code, String message) {
        if (!ok) {
            throw new ApiException(code, message, HttpStatus.BAD_REQUEST);
        }
    }
}
