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
    /**
     * Markdown 正文（用于分块/向量/召回）。与 {@link #bodyRich} 二选一：
     * 若 {@link #bodyRich} 非空，服务端<strong>忽略本字段</strong>，一律由富文本转 Markdown。
     */
    @Size(max = 524288)
    private String body;
    /**
     * 富文本 HTML；非空时由服务端转为 Markdown 后入库并向量化（与分块一致）。
     */
    @Size(max = 1048576)
    private String bodyRich;

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
