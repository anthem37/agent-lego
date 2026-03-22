package com.agentlego.backend.kb.application.validation;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.application.dto.KbCollectionDocumentValidationItemDto;
import com.agentlego.backend.kb.application.dto.KbCollectionDocumentsValidationResponse;
import com.agentlego.backend.kb.application.dto.KbDocumentValidationResponse;
import com.agentlego.backend.kb.application.dto.KbValidationIssueDto;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbDocumentRow;
import com.agentlego.backend.kb.support.KbKnowledgeInlineToolSyntax;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolCategory;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识库文档校验（不写库）：状态、工具绑定、{@code {{tool:}}}、{@code tool_field} 与 bindings 一致性。
 * <p>
 * 出参字段嵌入（{@code {{tool_field:…}}} / bindings 中 {@code tool_field:} 占位）<strong>仅允许「查询」类工具</strong>
 * （{@link ToolCategory#QUERY}）；操作类（{@link ToolCategory#ACTION}）不可用。
 */
@Component
public class KbDocumentValidator {

    private static final Pattern TOOL_FIELD_TOKEN_IN_BODY =
            Pattern.compile("\\{\\{\\s*(tool_field:[^}]+?)\\s*\\}\\}");

    private final ObjectMapper objectMapper;
    private final ToolRepository toolRepository;

    public KbDocumentValidator(ObjectMapper objectMapper, ToolRepository toolRepository) {
        this.objectMapper = objectMapper;
        this.toolRepository = toolRepository;
    }

    private static boolean isQueryCategory(ToolAggregate tool) {
        if (tool == null) {
            return false;
        }
        ToolCategory c = tool.getToolCategory();
        return c == ToolCategory.QUERY;
    }

    /**
     * {@code token} 形如 {@code tool_field:运行时名.点分路径} 或 {@code tool_field:纯数字ID.路径}。
     */
    private static String toolRefFromToolFieldToken(String token) {
        if (token == null || !token.startsWith("tool_field:")) {
            return null;
        }
        String rest = token.substring("tool_field:".length()).trim();
        if (rest.isEmpty()) {
            return null;
        }
        int dot = rest.indexOf('.');
        if (dot < 0) {
            return rest;
        }
        String ref = rest.substring(0, dot).trim();
        return ref.isEmpty() ? null : ref;
    }

    public KbDocumentValidationResponse validateDocumentRow(KbDocumentRow doc) {
        List<KbValidationIssueDto> issues = new ArrayList<>();
        String st = doc.getStatus() == null ? "" : doc.getStatus().trim().toUpperCase(Locale.ROOT);
        if ("FAILED".equals(st)) {
            issues.add(new KbValidationIssueDto(
                    "ERROR",
                    "INGEST_FAILED",
                    doc.getErrorMessage() == null || doc.getErrorMessage().isBlank()
                            ? "文档入库失败"
                            : doc.getErrorMessage()
            ));
        } else if ("PENDING".equals(st) || st.isEmpty()) {
            issues.add(new KbValidationIssueDto(
                    "WARN",
                    "NOT_READY",
                    "文档尚未就绪（PENDING），分片/向量可能未完成，召回测试可能无结果"
            ));
        }
        String body = doc.getBody() == null ? "" : doc.getBody();
        if (body.isBlank()) {
            issues.add(new KbValidationIssueDto("WARN", "EMPTY_BODY", "正文为空，无法进行工具引用与 tool_field 校验"));
        }
        try {
            validateKbDocumentToolLinks(doc.getLinkedToolIdsJson(), doc.getToolOutputBindingsJson());
        } catch (ApiException e) {
            issues.add(new KbValidationIssueDto("ERROR", e.getCode(), e.getMessage()));
        }
        if (!body.isBlank()) {
            try {
                validateBodyInlineToolMentions(body, doc.getLinkedToolIdsJson());
            } catch (ApiException e) {
                issues.add(new KbValidationIssueDto("ERROR", e.getCode(), e.getMessage()));
            }
            try {
                validateBodyToolFieldQueryTools(body, doc.getLinkedToolIdsJson());
            } catch (ApiException e) {
                issues.add(new KbValidationIssueDto("ERROR", e.getCode(), e.getMessage()));
            }
            collectToolFieldBindingConsistency(body, doc.getToolOutputBindingsJson(), issues);
        }
        KbDocumentValidationResponse resp = new KbDocumentValidationResponse();
        resp.setIssues(issues);
        resp.setOk(issues.stream().noneMatch(i -> "ERROR".equalsIgnoreCase(i.getSeverity())));
        return resp;
    }

    public KbCollectionDocumentsValidationResponse validateCollectionDocuments(
            KbCollectionAggregate col,
            List<KbDocumentRow> rows,
            boolean includeIssues
    ) {
        List<KbCollectionDocumentValidationItemDto> items = new ArrayList<>();
        int documentsOk = 0;
        int documentsWithErrors = 0;
        int documentsWithWarningsOnly = 0;
        for (KbDocumentRow row : rows) {
            KbDocumentValidationResponse one = validateDocumentRow(row);
            List<KbValidationIssueDto> issues = one.getIssues() == null ? List.of() : one.getIssues();
            int errorCount = 0;
            int warnCount = 0;
            int infoCount = 0;
            for (KbValidationIssueDto i : issues) {
                String sev = i.getSeverity() == null ? "" : i.getSeverity().trim().toUpperCase(Locale.ROOT);
                if ("ERROR".equals(sev)) {
                    errorCount++;
                } else if ("WARN".equals(sev) || "WARNING".equals(sev)) {
                    warnCount++;
                } else {
                    infoCount++;
                }
            }
            boolean docOk = one.isOk();
            if (!docOk) {
                documentsWithErrors++;
            } else if (warnCount > 0 || infoCount > 0) {
                documentsWithWarningsOnly++;
            } else {
                documentsOk++;
            }
            KbCollectionDocumentValidationItemDto item = new KbCollectionDocumentValidationItemDto();
            item.setDocumentId(row.getId());
            item.setTitle(row.getTitle() == null ? "" : row.getTitle());
            item.setOk(docOk);
            item.setErrorCount(errorCount);
            item.setWarnCount(warnCount);
            item.setInfoCount(infoCount);
            item.setIssues(includeIssues ? new ArrayList<>(issues) : null);
            items.add(item);
        }
        items.sort(Comparator
                .comparing(KbCollectionDocumentValidationItemDto::isOk)
                .thenComparing(i -> i.getTitle() == null ? "" : i.getTitle(), String.CASE_INSENSITIVE_ORDER));
        KbCollectionDocumentsValidationResponse out = new KbCollectionDocumentsValidationResponse();
        out.setCollectionId(col.getId());
        out.setCollectionName(col.getName() == null ? "" : col.getName());
        out.setTotalDocuments(rows.size());
        out.setDocumentsOk(documentsOk);
        out.setDocumentsWithErrors(documentsWithErrors);
        out.setDocumentsWithWarningsOnly(documentsWithWarningsOnly);
        out.setItems(items);
        return out;
    }

    public List<String> parseLinkedToolIdArray(String json) {
        try {
            JsonNode n = objectMapper.readTree(json);
            List<String> out = new ArrayList<>();
            if (n.isArray()) {
                for (JsonNode x : n) {
                    if (x != null && x.isTextual()) {
                        String t = x.asText().trim();
                        if (!t.isEmpty()) {
                            out.add(t);
                        }
                    }
                }
            }
            return out;
        } catch (Exception e) {
            throw new ApiException("VALIDATION_ERROR", "linkedToolIds JSON 无效", HttpStatus.BAD_REQUEST);
        }
    }

    public void validateKbDocumentToolLinks(String linkedIdsJson, String bindingsJson) {
        List<String> linked = parseLinkedToolIdArray(linkedIdsJson);
        Set<String> linkedLc = new HashSet<>();
        for (String id : linked) {
            linkedLc.add(id.toLowerCase(Locale.ROOT));
            toolRepository.findById(id)
                    .orElseThrow(() -> new ApiException("NOT_FOUND", "关联工具未找到: " + id, HttpStatus.NOT_FOUND));
        }
        try {
            JsonNode root = objectMapper.readTree(bindingsJson == null ? "{}" : bindingsJson);
            JsonNode mappings = root.get("mappings");
            if (mappings != null && mappings.isArray()) {
                for (JsonNode m : mappings) {
                    if (m == null || !m.isObject()) {
                        continue;
                    }
                    String tid = m.path("toolId").asText("").trim();
                    if (tid.isEmpty()) {
                        continue;
                    }
                    ToolAggregate tool = toolRepository.findById(tid)
                            .orElseThrow(() -> new ApiException("NOT_FOUND", "绑定中的工具未找到: " + tid, HttpStatus.NOT_FOUND));
                    if (!linkedLc.contains(tid.toLowerCase(Locale.ROOT))) {
                        throw new ApiException(
                                "VALIDATION_ERROR",
                                "toolOutputBindings 中的 toolId 必须出现在 linkedToolIds 中: " + tid,
                                HttpStatus.BAD_REQUEST
                        );
                    }
                    String placeholder = m.path("placeholder").asText("").trim();
                    if (placeholder.startsWith("tool_field:") && !isQueryCategory(tool)) {
                        throw new ApiException(
                                "VALIDATION_ERROR",
                                "tool_field 类占位仅允许绑定「查询」类工具（toolCategory=QUERY）；工具 "
                                        + (tool.getName() != null ? tool.getName() : tool.getId())
                                        + " 当前为操作类（ACTION）",
                                HttpStatus.BAD_REQUEST
                        );
                    }
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("VALIDATION_ERROR", "toolOutputBindings 无效", HttpStatus.BAD_REQUEST);
        }
    }

    public void validateBodyInlineToolMentions(String body, String linkedIdsJson) {
        List<String> linked = parseLinkedToolIdArray(linkedIdsJson);
        List<String> bad = KbKnowledgeInlineToolSyntax.validateMentionTokensAgainstLinked(body, linked, toolRepository);
        if (!bad.isEmpty()) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "正文 {{tool:…}} 须引用本条已绑定的工具：推荐写运行时名称 name（如 {{tool:order_query}}）；纯数字为工具 ID（兼容）。无法解析："
                            + String.join("、", bad),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * 正文中的 {@code {{tool_field:工具.路径}}}：仅允许「查询」类工具（{@link ToolCategory#QUERY}）绑定出参字段。
     */
    public void validateBodyToolFieldQueryTools(String body, String linkedIdsJson) {
        if (body == null || body.isBlank()) {
            return;
        }
        List<String> linked = parseLinkedToolIdArray(linkedIdsJson);
        Set<String> linkedLc = new HashSet<>();
        for (String id : linked) {
            linkedLc.add(id.toLowerCase(Locale.ROOT));
        }
        Matcher m = TOOL_FIELD_TOKEN_IN_BODY.matcher(body);
        Set<String> checkedToolRefs = new HashSet<>();
        while (m.find()) {
            String token = m.group(1).trim();
            String toolRef = toolRefFromToolFieldToken(token);
            if (toolRef == null || toolRef.isEmpty()) {
                throw new ApiException("VALIDATION_ERROR", "无效的 tool_field 占位：" + token, HttpStatus.BAD_REQUEST);
            }
            if (!checkedToolRefs.add(toolRef.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Optional<ToolAggregate> agg = KbKnowledgeInlineToolSyntax.resolveLinkedTool(
                    toolRef, linked, linkedLc, toolRepository);
            if (agg.isEmpty()) {
                throw new ApiException(
                        "VALIDATION_ERROR",
                        "tool_field 引用的工具须在本条 linkedToolIds 中（运行时 name 或工具 ID）：" + toolRef,
                        HttpStatus.BAD_REQUEST
                );
            }
            if (!isQueryCategory(agg.get())) {
                ToolAggregate t = agg.get();
                throw new ApiException(
                        "VALIDATION_ERROR",
                        "仅「查询」类工具（toolCategory=QUERY）可在知识正文嵌入出参字段（tool_field）；当前工具「"
                                + (t.getName() != null ? t.getName() : t.getId())
                                + "」为操作类（ACTION），请改为查询类工具或移除 tool_field",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
    }

    private void collectToolFieldBindingConsistency(
            String body,
            String bindingsJson,
            List<KbValidationIssueDto> issues
    ) {
        Set<String> inBody = new LinkedHashSet<>();
        Matcher m = TOOL_FIELD_TOKEN_IN_BODY.matcher(body);
        while (m.find()) {
            inBody.add(m.group(1).trim());
        }
        Set<String> inMappings = new LinkedHashSet<>();
        try {
            JsonNode root = objectMapper.readTree(bindingsJson == null ? "{}" : bindingsJson);
            JsonNode mappings = root.get("mappings");
            if (mappings != null && mappings.isArray()) {
                for (JsonNode item : mappings) {
                    if (item != null && item.isObject() && item.has("placeholder")) {
                        String ph = item.get("placeholder").asText("").trim();
                        if (!ph.isEmpty()) {
                            inMappings.add(ph);
                        }
                    }
                }
            }
        } catch (Exception e) {
            issues.add(new KbValidationIssueDto("WARN", "BINDINGS_PARSE", "无法解析 toolOutputBindings：" + e.getMessage()));
            return;
        }
        for (String token : inBody) {
            if (!inMappings.contains(token)) {
                issues.add(new KbValidationIssueDto(
                        "WARN",
                        "TOOL_FIELD_UNBOUND",
                        "正文中出现 {{" + token + "}}，但 toolOutputBindings.mappings 中无相同 placeholder"
                ));
            }
        }
        for (String ph : inMappings) {
            if (!ph.startsWith("tool_field:")) {
                continue;
            }
            Pattern p = Pattern.compile("\\{\\{\\s*" + Pattern.quote(ph) + "\\s*\\}\\}");
            if (!p.matcher(body).find()) {
                issues.add(new KbValidationIssueDto(
                        "INFO",
                        "UNUSED_PLACEHOLDER",
                        "bindings 中的 placeholder 未在正文出现：" + ph
                ));
            }
        }
    }
}
