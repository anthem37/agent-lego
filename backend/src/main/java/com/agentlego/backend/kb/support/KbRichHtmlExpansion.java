package com.agentlego.backend.kb.support;

import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.util.*;

/**
 * 入库前将富文本中的知识库标签展开为 Markdown 中的 {@code {{tool:运行时名}}} / {@code {{tool_field:运行时名.点分路径}}}，
 * 并为 {@code tool_field} 标签<strong>自动生成</strong> {@code toolOutputBindings.mappings}（placeholder 与正文 token 一致，便于召回匹配与按绑定替换）。
 * <p>
 * <strong>约束</strong>：出参字段嵌入仅适用于「查询」类工具（{@link com.agentlego.backend.tool.domain.ToolCategory#QUERY}），
 * 入库与控制台校验由 {@link com.agentlego.backend.kb.application.validation.KbDocumentValidator} 强制执行。
 * <p>
 * 与前端约定（属性可用 {@code data-*} 形式，兼容无 data- 前缀的 {@code tool-code} / {@code tool-field}）：
 * <ul>
 *     <li>{@code data-type="tool"} + {@code data-tool-code} → {@code {{tool:code}}}</li>
 *     <li>{@code tool_field} + {@code data-tool-code} + {@code data-tool-field}（如 {@code field1}、{@code data.orderNo}）→
 *     {@code {{tool_field:code.路径}}}，mapping 的 {@code placeholder} 为不含括号的 {@code tool_field:code.路径}</li>
 * </ul>
 * 仍兼容旧版 {@code span.kb-tool-mention} / {@code span.kb-ph-embed}。
 * <p>
 * 与 Quill 一致：{@code span.kb-knowledge-inline} 可能仅有 {@code data-tool-field} / {@code data-tool-code} 而缺少
 * {@code data-type}，仍须按 tool_field / tool 展开，否则 HTML→Markdown 会破坏占位符。
 */
public final class KbRichHtmlExpansion {

    private static final int MAX_AUTO_MAPPINGS = 64;

    private KbRichHtmlExpansion() {
    }

    /**
     * @param linkedIdsJson {@code linkedToolIds} 规范化后的 JSON 数组字符串
     */
    public static ExpandOutcome expandForIngest(
            String html,
            String linkedIdsJson,
            ToolRepository toolRepository,
            ObjectMapper om
    ) {
        if (html == null || html.isBlank()) {
            return new ExpandOutcome("", KbDocumentToolBindings.defaultBindingsJson());
        }
        List<String> linked = parseLinkedIds(linkedIdsJson);
        Set<String> linkedLc = new LinkedHashSet<>();
        for (String id : linked) {
            linkedLc.add(id.trim().toLowerCase(Locale.ROOT));
        }
        Map<String, ToolAggregate> toolsByLinkedIdLc =
                KbKnowledgeInlineToolSyntax.loadToolsByLinkedIds(linked, toolRepository);
        Document doc = Jsoup.parseBodyFragment(html);
        Element body = doc.body();
        ArrayNode mappings = om.createArrayNode();
        Map<String, String> dedupeKeyToInnerToken = new LinkedHashMap<>();

        // 1) tool_field：{{tool_field:运行时名.点分路径}}，与 mappings.placeholder 一致（含 Quill .kb-knowledge-inline）
        LinkedHashSet<Element> toolFieldEls = new LinkedHashSet<>();
        toolFieldEls.addAll(body.select("[data-type=tool_field], [data-type=TOOL_FIELD]"));
        toolFieldEls.addAll(body.select(".kb-knowledge-inline[data-tool-field]"));
        for (Element el : toolFieldEls) {
            String code = attr(el, "data-tool-code", "tool-code");
            String field = attr(el, "data-tool-field", "tool-field");
            if (code.isEmpty() || field.isEmpty()) {
                el.replaceWith(new TextNode("〔无效的工具字段标签〕"));
                continue;
            }
            var resolved = KbKnowledgeInlineToolSyntax.resolveLinkedTool(code, linked, linkedLc, toolsByLinkedIdLc);
            if (resolved.isEmpty()) {
                throw new IllegalArgumentException(
                        "tool_field 无法解析工具（须为已绑定工具的运行时名称或 ID）：" + code
                );
            }
            String toolId = resolved.get().getId();
            String jsonPath = toJsonPath(field);
            if (jsonPath == null) {
                throw new IllegalArgumentException("tool-field 非法（示例 data.orderNo）：" + field);
            }
            String dedupe = toolId + "\0" + jsonPath;
            String innerToken = dedupeKeyToInnerToken.computeIfAbsent(
                    dedupe,
                    k -> {
                        String t = toolFieldMarkdownToken(code, field);
                        if (!KbDocumentToolBindings.isValidPlaceholderKey(t)) {
                            throw new IllegalArgumentException("tool_field 展开后占位符过长或含非法字符：" + t);
                        }
                        if (mappings.size() >= MAX_AUTO_MAPPINGS) {
                            throw new IllegalArgumentException("tool_field 映射过多（最多 " + MAX_AUTO_MAPPINGS + " 条）");
                        }
                        ObjectNode one = om.createObjectNode();
                        one.put("placeholder", t);
                        one.put("toolId", toolId);
                        one.put("jsonPath", jsonPath);
                        mappings.add(one);
                        return t;
                    }
            );
            el.replaceWith(new TextNode("{{" + innerToken + "}}"));
        }

        // 2) tool：显式 data-type=tool，或 .kb-knowledge-inline 仅有 tool-code、无 tool-field
        LinkedHashSet<Element> toolEls = new LinkedHashSet<>();
        toolEls.addAll(body.select("[data-type=tool], [data-type=TOOL]"));
        for (Element el : body.select(".kb-knowledge-inline[data-tool-code]")) {
            if (!attr(el, "data-tool-field", "tool-field").isEmpty()) {
                continue;
            }
            toolEls.add(el);
        }
        for (Element el : toolEls) {
            String code = attr(el, "data-tool-code", "tool-code");
            if (!code.isEmpty()) {
                el.replaceWith(new TextNode("{{tool:" + code + "}}"));
            } else {
                el.replaceWith(new TextNode("〔无效的工具标签〕"));
            }
        }

        // 3) 旧版 span
        for (Element el : body.select("span.kb-tool-mention[data-kb-tool]")) {
            String token = el.attr("data-kb-tool").trim();
            if (!token.isEmpty()) {
                el.replaceWith(new TextNode("{{tool:" + token + "}}"));
            }
        }
        for (Element el : body.select("span.kb-ph-embed[data-kb-placeholder]")) {
            String key = el.attr("data-kb-placeholder").trim();
            if (!key.isEmpty()) {
                el.replaceWith(new TextNode("{{" + key + "}}"));
            }
        }

        ObjectNode root = om.createObjectNode();
        root.set("mappings", mappings);
        String bindingsJson;
        try {
            bindingsJson = om.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法序列化自动生成的 toolOutputBindings");
        }
        return new ExpandOutcome(body.html(), bindingsJson);
    }

    private static String attr(Element el, String... names) {
        for (String n : names) {
            String v = el.attr(n);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    /**
     * {@code data.orderNo} → {@code $.data.orderNo}；已是 {@code $....} 则原样返回（trim）。
     */
    static String toJsonPath(String toolField) {
        if (toolField == null) {
            return null;
        }
        String f = toolField.trim();
        if (f.isEmpty()) {
            return null;
        }
        if (f.startsWith("$")) {
            return f;
        }
        if (!f.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '[' || c == ']')) {
            return null;
        }
        return "$." + f;
    }

    /**
     * 正文与 mappings.placeholder 共用，不含外层 {@code {{ }}}
     */
    static String toolFieldMarkdownToken(String toolRuntimeCode, String fieldDotPath) {
        return "tool_field:" + toolRuntimeCode.trim() + "." + fieldDotPath.trim();
    }

    private static List<String> parseLinkedIds(String linkedIdsJson) {
        if (linkedIdsJson == null || linkedIdsJson.isBlank() || "[]".equals(linkedIdsJson.trim())) {
            return List.of();
        }
        try {
            var om = new ObjectMapper();
            var n = om.readTree(linkedIdsJson);
            List<String> out = new ArrayList<>();
            if (n.isArray()) {
                for (var x : n) {
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
            return List.of();
        }
    }

    public record ExpandOutcome(String html, String bindingsJson) {
    }
}
