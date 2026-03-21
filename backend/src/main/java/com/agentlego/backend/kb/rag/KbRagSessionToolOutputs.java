package com.agentlego.backend.kb.rag;

import com.agentlego.backend.kb.support.KbToolResultRootExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单次智能体请求内：按平台工具 ID 保存当前已执行成功的工具 JSON 根对象，供 RAG 片段渲染时替换
 * {@code tool_field} 占位符。不做跨请求持久化，也不做「性能缓存」层；仅为同一次 run 内的功能数据。
 */
public final class KbRagSessionToolOutputs {

    private final ConcurrentHashMap<String, Object> byToolId = new ConcurrentHashMap<>();

    /**
     * 记录成功工具结果（同 id 覆盖为最新一次）。
     */
    public void recordSuccess(String platformToolId, ToolResultBlock block, ObjectMapper om) {
        if (platformToolId == null || platformToolId.isBlank()) {
            return;
        }
        Object root = KbToolResultRootExtractor.extractRoot(block, om);
        if (root != null) {
            byToolId.put(platformToolId.trim(), root);
        }
    }

    /**
     * 供 {@link com.agentlego.backend.kb.runtime.KbVectorKnowledge} 在单次 retrieve 中读取。
     * 返回只读视图，<strong>不对整表做拷贝</strong>（避免把「快照」当缓存优化）；映射会随后续工具调用更新。
     */
    public Map<String, Object> forExpansion() {
        return Collections.unmodifiableMap(byToolId);
    }
}
