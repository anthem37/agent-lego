package com.agentlego.backend.kb.rag;

import io.agentscope.core.rag.model.RetrieveConfig;

import java.util.List;

/**
 * 外置向量库上的知识检索，由 {@link KbVectorRetrieveEngine} 实现，供 {@link com.agentlego.backend.kb.runtime.KbVectorKnowledge} 使用。
 */
public interface KbVectorRetrieve {

    List<KbRagRankedChunk> search(String query, RetrieveConfig config);
}
