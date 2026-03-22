package com.agentlego.backend.kb.vector;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 从集合持久化的 JSON 解析向量库连接配置；运行时空配置与创建时校验分层（创建走 {@link KbVectorStoreConfigValidator}）。
 */
public final class KbVectorStoreConfigMaps {

    private KbVectorStoreConfigMaps() {
    }

    /**
     * @param kindLabel 错误文案用，如 {@code "Milvus"}、{@code "Qdrant"}
     */
    public static Map<String, Object> requireNonEmptyFromAggregate(KbCollectionAggregate col, String kindLabel) {
        if (col == null) {
            throw new ApiException("VALIDATION_ERROR", "集合为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> m = JsonMaps.parseObject(col.getVectorStoreConfigJson());
        if (m.isEmpty()) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "该集合的向量库配置为空（vector_store_config 未填写或仅为 {}）。"
                            + "请删除该集合后重新创建并填写 "
                            + kindLabel
                            + " 连接与 collectionName，或补全表 lego_kb_collections.vector_store_config。",
                    HttpStatus.BAD_REQUEST
            );
        }
        return m;
    }
}
