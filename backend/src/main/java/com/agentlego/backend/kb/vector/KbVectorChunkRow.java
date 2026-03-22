package com.agentlego.backend.kb.vector;

/**
 * 单条写入外置向量库的分片向量（Milvus / Qdrant 等共用）。
 */
public record KbVectorChunkRow(String chunkId, String documentId, int chunkIndex, String text, float[] vector) {
}
