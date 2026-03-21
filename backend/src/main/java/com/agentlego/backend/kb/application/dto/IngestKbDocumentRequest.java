package com.agentlego.backend.kb.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class IngestKbDocumentRequest {
    @NotBlank
    @Size(max = 512)
    private String title;
    @NotBlank
    @Size(max = 524288)
    private String body;

    /**
     * 可选：拼入每条分片的 embedding 输入，提升召回；最多 32 条，每条最长 512 字符
     */
    @Size(max = 32)
    private List<@Size(max = 512) String> similarQueries;

    /**
     * 可选；本条知识绑定的平台工具 ID（最多 32 个）。正文可用 {@code {{tool:<id>}}} 引用工具名，出参占位符见 toolOutputBindings。
     */
    private List<String> linkedToolIds;

    /**
     * 可选；工具 JSON 出参字段映射到正文 {@code {{placeholder}}}。
     */
    private Map<String, Object> toolOutputBindings;
}
