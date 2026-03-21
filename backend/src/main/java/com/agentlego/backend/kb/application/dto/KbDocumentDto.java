package com.agentlego.backend.kb.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KbDocumentDto {
    private String id;
    private String collectionId;
    private String title;
    /**
     * 列表接口不返回以减轻负载；GET 单条详情时返回入库原文。
     */
    private String body;
    private String status;
    private String errorMessage;
    /**
     * 本条知识绑定的工具 ID。
     */
    private List<String> linkedToolIds;
    /**
     * 出参 → 占位符绑定。
     */
    private Map<String, Object> toolOutputBindings;
    private Instant createdAt;
    private Instant updatedAt;
}
