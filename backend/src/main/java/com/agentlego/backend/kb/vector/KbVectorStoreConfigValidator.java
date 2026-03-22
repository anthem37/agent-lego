package com.agentlego.backend.kb.vector;

import com.agentlego.backend.kb.domain.KbVectorStoreKind;
import com.agentlego.backend.kb.milvus.KbMilvusSettings;
import com.agentlego.backend.kb.qdrant.KbQdrantSettings;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KbVectorStoreConfigValidator {

    public void validate(KbVectorStoreKind kind, Map<String, Object> config) {
        validate(kind, config, true);
    }

    /**
     * @param requireCollectionName false 用于公共向量库 profile（仅连接）；知识库合并后的配置仍须带物理 collection 名。
     */
    public void validate(KbVectorStoreKind kind, Map<String, Object> config, boolean requireCollectionName) {
        if (kind == null) {
            kind = KbVectorStoreKind.MILVUS;
        }
        switch (kind) {
            case MILVUS -> KbMilvusSettings.fromConfigMap(config, requireCollectionName);
            case QDRANT -> KbQdrantSettings.fromConfigMap(config, requireCollectionName);
        }
    }
}
