package com.agentlego.backend.kb.application.dto;

import lombok.Data;

@Data
public class KbValidateCollectionDocumentsRequest {
    /**
     * 为 true 时在每条文档结果中返回完整 issues 列表；默认 false 仅返回计数，避免文档很多时响应过大。
     */
    private Boolean includeIssues;
}
