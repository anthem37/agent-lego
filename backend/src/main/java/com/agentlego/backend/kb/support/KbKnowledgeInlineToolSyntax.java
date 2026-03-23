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
     * 将 {@code linkedToolIds} 对应工具一次性加载为 Map（key 为工具 id 的小写），供正文解析循环复用，避免逐条 {@link ToolRepository#findById(String)}。
     */
    public static Map<String, ToolAggregate> loadToolsByLinkedIds(
            List<String> linkedToolIds,
            ToolRepository toolRepository
    ) {
        Map<String, ToolAggregate> byIdLc = new HashMap<>();
        if (linkedToolIds == null || linkedToolIds.isEmpty() || toolRepository == null) {
            return byIdLc;
        }
        List<String> ids = linkedToolIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return byIdLc;
        }
        for (ToolAggregate agg : toolRepository.findByIds(ids)) {
            if (agg != null && agg.getId() != null && !agg.getId().isBlank()) {
                byIdLc.put(agg.getId().trim().toLowerCase(Locale.ROOT), agg);
            }
        }
        return byIdLc;
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
        Map<String, ToolAggregate> byIdLc = loadToolsByLinkedIds(linkedToolIds, toolRepository);
        Matcher m = TOOL_MENTION.matcher(body);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String token = m.group(1).trim();
            String repl;
            if (token.isEmpty() || token.length() > MAX_TOKEN_LEN) {
                repl = "〔无效的工具引用〕";
            } else {
                Optional<ToolAggregate> resolved = resolveLinkedTool(token, linkedToolIds, linkedLc, byIdLc);
                repl = resolved.map(KbKnowledgeInlineToolSyntax::formatDisplayLabel)
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
     * <p>
     * 推荐传入 {@link #loadToolsByLinkedIds(List, ToolRepository)} 预加载的 Map，避免循环内重复查库。
     */
    public static Optional<ToolAggregate> resolveLinkedTool(
            String token,
            List<String> linkedToolIds,
            Set<String> linkedIdsLowercase,
            Map<String, ToolAggregate> toolsByLinkedIdLowercase
    ) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        if (toolsByLinkedIdLowercase == null) {
            toolsByLinkedIdLowercase = Map.of();
        }
        String t = token.trim();
        if (looksLikeNumericToolId(t)) {
            String lc = t.toLowerCase(Locale.ROOT);
            if (!linkedIdsLowercase.contains(lc)) {
                return Optional.empty();
            }
            return Optional.ofNullable(toolsByLinkedIdLowercase.get(lc));
        }
        for (String id : linkedToolIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String idLc = id.trim().toLowerCase(Locale.ROOT);
            if (!linkedIdsLowercase.contains(idLc)) {
                continue;
            }
            ToolAggregate agg = toolsByLinkedIdLowercase.get(idLc);
            if (agg == null) {
                continue;
            }
            String n = agg.getName();
            if (n != null && n.trim().equalsIgnoreCase(t)) {
                return Optional.of(agg);
            }
        }
        return Optional.empty();
    }

    /**
     * 单次解析：内部会批量加载绑定工具；若在循环中调用请改用 {@link #loadToolsByLinkedIds(List, ToolRepository)} +
     * {@link #resolveLinkedTool(String, List, Set, Map)}。
     */
    public static Optional<ToolAggregate> resolveLinkedTool(
            String token,
            List<String> linkedToolIds,
            Set<String> linkedIdsLowercase,
            ToolRepository toolRepository
    ) {
        Map<String, ToolAggregate> byIdLc = loadToolsByLinkedIds(linkedToolIds, toolRepository);
        return resolveLinkedTool(token, linkedToolIds, linkedIdsLowercase, byIdLc);
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
        if (linkedToolIds == null) {
            linkedToolIds = List.of();
        }
        Set<String> linkedLc = new LinkedHashSet<>();
        for (String id : linkedToolIds) {
            if (id != null && !id.isBlank()) {
                linkedLc.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        Map<String, ToolAggregate> byIdLc = loadToolsByLinkedIds(linkedToolIds, toolRepository);
        for (String raw : extractToolMentionTokens(body)) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            Optional<ToolAggregate> resolved = resolveLinkedTool(token, linkedToolIds, linkedLc, byIdLc);
            if (resolved.isEmpty()) {
                errors.add(token);
            }
        }
        return errors;
    }
}
