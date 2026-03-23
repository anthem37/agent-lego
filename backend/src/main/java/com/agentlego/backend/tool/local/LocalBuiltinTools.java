package com.agentlego.backend.tool.local;

import com.agentlego.backend.common.JacksonHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 进程内 LOCAL 内置宿主 Bean：在方法上使用 {@link Tool} 注册具体工具。
 * <p>
 * 以下为通用、无外部依赖的基础能力，便于智能体与联调使用；可按业务继续追加 {@code @Tool} 方法。
 */
@Component
public class LocalBuiltinTools {

    private static final int MAX_GENERAL_TEXT_CHARS = 256_000;
    private static final int MAX_JSON_CHARS = 512_000;
    private static final int UUID_MAX_COUNT = 32;

    private static final Pattern WS_RUN = Pattern.compile("\\s+");

    // ---------------------------------------------------------------------
    // 1) 当前时间（服务器时区可配）
    // ---------------------------------------------------------------------

    @Tool(
            name = "time_now",
            description = "获取当前服务器时间：UTC 的 ISO-8601 时刻、epoch 秒/毫秒；可选 IANA 时区（如 Asia/Shanghai）时输出该时区下的本地时间。",
            converter = PlainTextToolResultConverter.class
    )
    public String time_now(
            @ToolParam(name = "timezone", description = "IANA 时区 ID，如 Asia/Shanghai；可空（仅 UTC 对照）", required = false)
            String timezone
    ) {
        Instant now = Instant.now();
        StringBuilder sb = new StringBuilder();
        sb.append("utcInstant=").append(now.toString()).append('\n');
        sb.append("epochSeconds=").append(now.getEpochSecond()).append('\n');
        sb.append("epochMillis=").append(now.toEpochMilli()).append('\n');
        if (timezone == null || timezone.isBlank()) {
            sb.append("note=未指定 timezone，以上为 UTC（Instant）。");
            return sb.toString();
        }
        String zoneIdStr = timezone.trim();
        try {
            ZoneId z = ZoneId.of(zoneIdStr);
            ZonedDateTime zdt = now.atZone(z);
            sb.append("zoneId=").append(z.getId()).append('\n');
            sb.append("zonedDateTime=").append(zdt.toString());
        } catch (DateTimeException ex) {
            sb.append("error=无效的时区 ID：").append(zoneIdStr).append("（").append(ex.getMessage()).append("）");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // 2) 文本变换
    // ---------------------------------------------------------------------

    @Tool(
            name = "text_transform",
            description = "对文本做常用变换：去首尾空白、大小写、合并连续空白、统一换行符等；输入长度有上限。",
            converter = PlainTextToolResultConverter.class
    )
    public String text_transform(
            @ToolParam(name = "text", description = "原始文本", required = true)
            String text,
            @ToolParam(
                    name = "operation",
                    description = "变换类型：TRIM / UPPER / LOWER / COLLAPSE_WHITESPACE / NORMALIZE_NEWLINES（大小写不敏感）",
                    required = true
            )
            String operation
    ) {
        if (text == null) {
            return "";
        }
        if (text.length() > MAX_GENERAL_TEXT_CHARS) {
            return "ERROR: text 长度超过上限 " + MAX_GENERAL_TEXT_CHARS;
        }
        String op = operation == null ? "" : operation.trim().toUpperCase(Locale.ROOT);
        return switch (op) {
            case "TRIM" -> text.strip();
            case "UPPER" -> text.toUpperCase(Locale.ROOT);
            case "LOWER" -> text.toLowerCase(Locale.ROOT);
            case "COLLAPSE_WHITESPACE" -> WS_RUN.matcher(text.strip()).replaceAll(" ");
            case "NORMALIZE_NEWLINES" -> text.replace("\r\n", "\n").replace('\r', '\n');
            default -> "ERROR: 未知 operation「" + operation + "」。请使用 TRIM / UPPER / LOWER / "
                    + "COLLAPSE_WHITESPACE / NORMALIZE_NEWLINES。";
        };
    }

    // ---------------------------------------------------------------------
    // 3) UUID
    // ---------------------------------------------------------------------

    @Tool(
            name = "uuid_generate",
            description = "批量生成随机 UUID（版本 4），每行一个。",
            converter = PlainTextToolResultConverter.class
    )
    public String uuid_generate(
            @ToolParam(name = "count", description = "生成数量 1–32，默认 1", required = false)
            Integer count
    ) {
        int n = count == null ? 1 : count;
        if (n < 1) {
            n = 1;
        }
        if (n > UUID_MAX_COUNT) {
            n = UUID_MAX_COUNT;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(UUID.randomUUID());
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // 4) JSON 格式化 / 压缩
    // ---------------------------------------------------------------------

    @Tool(
            name = "json_format",
            description = "校验并格式化 JSON：可美化缩进或压缩为单行；非法 JSON 时返回以 ERROR: 开头的说明。",
            converter = PlainTextToolResultConverter.class
    )
    public String json_format(
            @ToolParam(name = "json", description = "合法 JSON 文本（对象或数组等）", required = true)
            String json,
            @ToolParam(name = "compact", description = "true：压缩为一行；false：美化（默认）", required = false)
            Boolean compact
    ) {
        if (json == null) {
            return "ERROR: json 不能为空";
        }
        if (json.length() > MAX_JSON_CHARS) {
            return "ERROR: json 长度超过上限 " + MAX_JSON_CHARS;
        }
        boolean minify = Boolean.TRUE.equals(compact);
        try {
            JsonNode node = JacksonHolder.INSTANCE.readTree(json);
            if (minify) {
                return JacksonHolder.INSTANCE.writeValueAsString(node);
            }
            return JacksonHolder.INSTANCE.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            String detail = e.getOriginalMessage();
            if (detail == null || detail.isBlank()) {
                detail = e.getMessage();
            }
            return "ERROR: JSON 解析失败 — " + (detail == null ? "(unknown)" : detail);
        }
    }

    // ---------------------------------------------------------------------
    // 5) SHA-256 十六进制
    // ---------------------------------------------------------------------

    @Tool(
            name = "hash_sha256",
            description = "对 UTF-8 文本计算 SHA-256，返回小写十六进制字符串。",
            converter = PlainTextToolResultConverter.class
    )
    public String hash_sha256(
            @ToolParam(name = "text", description = "任意文本", required = true)
            String text
    ) {
        if (text == null) {
            return HexFormat.of().formatHex(digestBytes(new byte[0]));
        }
        if (text.length() > MAX_GENERAL_TEXT_CHARS) {
            return "ERROR: text 长度超过上限 " + MAX_GENERAL_TEXT_CHARS;
        }
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(digestBytes(utf8));
    }

    private static byte[] digestBytes(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
