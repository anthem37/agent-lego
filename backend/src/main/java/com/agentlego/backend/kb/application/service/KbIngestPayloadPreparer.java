package com.agentlego.backend.kb.application.service;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.application.dto.IngestKbDocumentRequest;
import com.agentlego.backend.kb.application.dto.KbPreparedIngestPayload;
import com.agentlego.backend.kb.application.validation.KbDocumentValidator;
import com.agentlego.backend.kb.support.*;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将 {@link IngestKbDocumentRequest} 规范化为可持久化正文、绑定 JSON 与相似问序列。
 */
@Component
public class KbIngestPayloadPreparer {

    private final ToolRepository toolRepository;
    private final ObjectMapper objectMapper;
    private final KbDocumentValidator documentValidator;
    private final int maxDocumentChars;

    public KbIngestPayloadPreparer(
            ToolRepository toolRepository,
            ObjectMapper objectMapper,
            KbDocumentValidator documentValidator,
            @Value("${agentlego.kb.ingest.max-document-chars:524288}") int maxDocumentChars
    ) {
        this.toolRepository = toolRepository;
        this.objectMapper = objectMapper;
        this.documentValidator = documentValidator;
        this.maxDocumentChars = maxDocumentChars;
    }

    public KbPreparedIngestPayload prepare(IngestKbDocumentRequest req) {
        String title = req.getTitle() == null ? "" : req.getTitle().trim();
        if (title.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "title 不能为空", HttpStatus.BAD_REQUEST);
        }
        if (title.length() > KbLimits.MAX_DOCUMENT_TITLE_CHARS) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "title 过长（最多 " + KbLimits.MAX_DOCUMENT_TITLE_CHARS + " 字符）",
                    HttpStatus.BAD_REQUEST
            );
        }

        String mdRaw = req.getBody() == null ? "" : req.getBody().trim();
        String richRaw = req.getBodyRich() == null ? "" : req.getBodyRich().trim();
        if (mdRaw.isEmpty() && richRaw.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "正文不能为空：请填写富文本（bodyRich）或 Markdown（body）", HttpStatus.BAD_REQUEST);
        }
        if (richRaw.length() > maxDocumentChars * 4) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "bodyRich 过长（最多约 " + (maxDocumentChars * 4) + " 字符），请拆分或精简",
                    HttpStatus.BAD_REQUEST
            );
        }
        String linkedJson;
        try {
            linkedJson = KbDocumentToolBindings.normalizeLinkedToolIdsJson(req.getLinkedToolIds());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        String htmlForMarkdown = richRaw;
        String bindingsJson;
        try {
            if (!richRaw.isEmpty()) {
                KbRichHtmlExpansion.ExpandOutcome expanded =
                        KbRichHtmlExpansion.expandForIngest(richRaw, linkedJson, toolRepository, objectMapper);
                htmlForMarkdown = expanded.html();
                bindingsJson = KbDocumentToolBindings.mergeBindingsJson(
                        expanded.bindingsJson(),
                        req.getToolOutputBindings(),
                        objectMapper
                );
            } else {
                bindingsJson = KbDocumentToolBindings.normalizeBindingsJson(req.getToolOutputBindings(), objectMapper);
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        String body = !richRaw.isEmpty() ? KbHtmlToMarkdown.convert(htmlForMarkdown) : mdRaw;
        if (body.isEmpty()) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "富文本转换后的 Markdown 为空，请检查内容",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (body.length() > maxDocumentChars) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "正文（Markdown）超过允许长度（最多 " + maxDocumentChars + " 字符），请拆分文档",
                    HttpStatus.BAD_REQUEST
            );
        }
        String bodyRichToStore = richRaw.isEmpty() ? null : richRaw;

        documentValidator.validateKbDocumentToolLinks(linkedJson, bindingsJson);
        documentValidator.validateBodyInlineToolMentions(body, linkedJson);
        documentValidator.validateBodyToolFieldQueryTools(body, linkedJson);

        List<String> similarQueriesNormalized = KbIngestEmbeddingInputs.normalizeSimilarQueries(req.getSimilarQueries());
        String similarQueriesJson;
        try {
            similarQueriesJson = objectMapper.writeValueAsString(similarQueriesNormalized);
        } catch (Exception e) {
            throw new ApiException("VALIDATION_ERROR", "相似问序列化失败", HttpStatus.BAD_REQUEST);
        }
        return new KbPreparedIngestPayload(
                title,
                body,
                bodyRichToStore,
                linkedJson,
                bindingsJson,
                similarQueriesNormalized,
                similarQueriesJson
        );
    }
}
