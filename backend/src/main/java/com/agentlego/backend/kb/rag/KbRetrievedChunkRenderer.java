package com.agentlego.backend.kb.rag;

import com.agentlego.backend.kb.domain.KbDocumentRow;
import com.agentlego.backend.kb.support.KbKnowledgeInlineToolSyntax;
import com.agentlego.backend.kb.support.KbLinkedToolIdsJson;
import com.agentlego.backend.kb.support.KbToolPlaceholderExpander;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将检索到的分片正文转为「注入模型前」文本：展开 {@code {{tool:…}}}，并按会话工具出参替换 {@code tool_field} 等占位符。
 */
@Component
public final class KbRetrievedChunkRenderer {

    private final ToolRepository toolRepository;
    private final ObjectMapper objectMapper;

    public KbRetrievedChunkRenderer(ToolRepository toolRepository, ObjectMapper objectMapper) {
        this.toolRepository = Objects.requireNonNull(toolRepository, "toolRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * @param rawChunkText 分片原始文本（来自 chunk 表）
     * @param document     可为 null（例如文档已删）；null 时不做工具相关展开
     * @param toolOutputs  平台工具 ID → 根 JSON（与 {@link KbToolPlaceholderExpander} 约定一致）
     */
    public String renderForModel(String rawChunkText, KbDocumentRow document, Map<String, Object> toolOutputs) {
        String raw = rawChunkText == null ? "" : rawChunkText;
        if (document == null) {
            return raw;
        }
        List<String> linked = KbLinkedToolIdsJson.parse(document.getLinkedToolIdsJson());
        Map<String, Object> outs = toolOutputs == null ? Map.of() : toolOutputs;
        // 与 KbApplicationService.renderDocumentBody 一致：先按 bindings 替换占位，再展开 {{tool:…}}
        String afterFields = KbToolPlaceholderExpander.expand(
                raw,
                document.getToolOutputBindingsJson(),
                outs,
                objectMapper
        );
        return KbKnowledgeInlineToolSyntax.expandToolMentions(afterFields, toolRepository, linked);
    }
}
