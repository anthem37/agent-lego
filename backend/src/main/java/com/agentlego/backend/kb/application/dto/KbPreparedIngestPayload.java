package com.agentlego.backend.kb.application.dto;

import java.util.List;

/**
 * 入库请求经校验与富文本展开后的中间结果，供 PG 写入与后续分片/向量化。
 */
public record KbPreparedIngestPayload(
        String title,
        String markdownBody,
        String bodyRichToStore,
        String linkedToolIdsJson,
        String toolOutputBindingsJson,
        List<String> similarQueriesNormalized,
        String similarQueriesJson
) {
}
