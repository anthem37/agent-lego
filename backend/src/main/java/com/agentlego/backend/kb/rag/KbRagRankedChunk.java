package com.agentlego.backend.kb.rag;

/**
 * 检索融合排序后的单条分片（尚未做文档级后处理）。
 */
public record KbRagRankedChunk(
        String chunkId,
        String documentId,
        String content,
        double score
) {
}
