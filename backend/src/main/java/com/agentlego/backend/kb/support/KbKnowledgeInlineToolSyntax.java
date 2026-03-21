package com.agentlego.backend.kb.support;

import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识正文中的工具引用：{@code {{tool:<标识>}}}。
 * <p>
 * <strong>推荐</strong>：{@code <标识>} 为平台工具的<strong>运行时名称</strong> {@code name}（如 {@code order_query}），便于人读、易写。<br>
 * <strong>兼容</strong>：纯数字时按 <strong>Snowflake 工具 ID</strong> 解析（历史内容或自动化插入）。
 */
public final class KbKnowledgeInlineToolSyntax {

    /**
     * token：不含空白与 `}`，长度受限；支持 name（含字母、数字、下划线、短横线等）或纯数字 ID。
     */
    private static final Pattern TOOL_MENTION = Pattern.compile("\\{\\{\\s*tool:([^}\\s]+)\\s*\\}\\}");
    private static final int MAX_TOKEN_LEN = 128;

    private KbKnowledgeInlineToolSyntax() {
    }

    /**
     * 从正文提取所有 {@code {{tool:…}}} 中的标识（去重保序）。
     */
    public static Set<String> extractToolMentionTokens(String body) {
        Set<String> out = new LinkedHashSet<>();
        if (body == null || body.isEmpty()) {
            return out;
        }
        Matcher m = TOOL_MENTION.matcher(body);
        while (m.find()) {
            String raw = m.group(1).trim();
            if (!raw.isEmpty() && raw.length() <= MAX_TOKEN_LEN) {
                out.add(raw);
            }
        }
        return out;
    }

    /**
     * @deprecated 请使用 {@link #extractToolMentionTokens(String)}；名称与行为一致（含 name 与数字 id）。
     */
    @Deprecated
    public static Set<String> extractToolMentionIds(String body) {
        return extractToolMentionTokens(body);
    }

    public static boolean looksLikeNumericToolId(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将 {@code {{tool:…}}} 展开为可读文案；仅解析<strong>本条知识已绑定</strong>的工具。
     *
     * @param linkedToolIds 文档绑定的工具 ID 列表（库中原始大小写）
     */
    public static String expandToolMentions(
            String body,
            ToolRepository toolRepository,
            List<String> linkedToolIds
    ) {
        if (body == null || body.isEmpty()) {
            return body == null ? "" : body;
        }
        if (linkedToolIds == null || linkedToolIds.isEmpty()) {
            return replaceAllMentions(body, (token) -> "〔工具未绑定：" + token + "〕");
        }
        Set<String> linkedLc = new LinkedHashSet<>();
        for (String id : linkedToolIds) {
            if (id != null && !id.isBlank()) {
                linkedLc.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        Matcher m = TOOL_MENTION.matcher(body);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String token = m.group(1).trim();
            String repl;
            if (token.isEmpty() || token.length() > MAX_TOKEN_LEN) {
                repl = "〔无效的工具引用〕";
            } else {
                Optional<ToolAggregate> agg = resolveLinkedTool(token, linkedToolIds, linkedLc, toolRepository);
                repl = agg.map(KbKnowledgeInlineToolSyntax::formatDisplayLabel)
                        .orElseGet(() -> "〔工具未绑定或不存在：" + token + "〕");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String replaceAllMentions(String body, java.util.function.Function<String, String> fn) {
        Matcher m = TOOL_MENTION.matcher(body);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String token = m.group(1).trim();
            m.appendReplacement(sb, Matcher.quoteReplacement(fn.apply(token)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 在已绑定列表内按 token 解析工具：纯数字 → id；否则按 name（忽略大小写）。
     */
    public static Optional<ToolAggregate> resolveLinkedTool(
            String token,
            List<String> linkedToolIds,
            Set<String> linkedIdsLowercase,
            ToolRepository toolRepository
    ) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String t = token.trim();
        if (looksLikeNumericToolId(t)) {
            String lc = t.toLowerCase(Locale.ROOT);
            if (!linkedIdsLowercase.contains(lc)) {
                return Optional.empty();
            }
            return toolRepository.findById(t);
        }
        for (String id : linkedToolIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String idLc = id.trim().toLowerCase(Locale.ROOT);
            if (!linkedIdsLowercase.contains(idLc)) {
                continue;
            }
            Optional<ToolAggregate> o = toolRepository.findById(id.trim());
            if (o.isEmpty()) {
                continue;
            }
            String n = o.get().getName();
            if (n != null && n.trim().equalsIgnoreCase(t)) {
                return o;
            }
        }
        return Optional.empty();
    }

    private static String formatDisplayLabel(ToolAggregate agg) {
        if (agg == null) {
            return "〔工具〕";
        }
        String d = agg.getDisplayLabel();
        if (d != null && !d.isBlank()) {
            return "「" + d.trim() + "」工具";
        }
        String n = agg.getName() == null ? "" : agg.getName().trim();
        if (n.isEmpty()) {
            return "〔工具〕";
        }
        return "「" + n + "」工具";
    }

    /**
     * 校验正文中每个引用是否对应已绑定工具（id 或 name）。
     */
    public static List<String> validateMentionTokensAgainstLinked(
            String body,
            List<String> linkedToolIds,
            ToolRepository toolRepository
    ) {
        List<String> errors = new ArrayList<>();
        Set<String> linkedLc = new LinkedHashSet<>();
        for (String id : linkedToolIds) {
            if (id != null && !id.isBlank()) {
                linkedLc.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (String raw : extractToolMentionTokens(body)) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            Optional<ToolAggregate> agg = resolveLinkedTool(token, linkedToolIds, linkedLc, toolRepository);
            if (agg.isEmpty()) {
                errors.add(token);
            }
        }
        return errors;
    }
}
