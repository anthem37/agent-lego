package com.agentlego.backend.tool.http;

import com.agentlego.backend.api.ApiException;
import org.springframework.http.HttpStatus;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 {@link com.agentlego.backend.tool.domain.ToolType#HTTP} 工具的 definition。
 * <p>
 * 约定字段：
 * <ul>
 *     <li>{@code url}（必填）：支持占位符 {@code {param}}，由调用入参替换并 URL 编码；</li>
 *     <li>{@code method}（可选）：GET、POST、PUT、PATCH、DELETE，默认 GET；</li>
 *     <li>{@code headers}（可选）：字符串键值头；</li>
 *     <li>{@code sendJsonBody}（可选）：对 POST/PUT/PATCH 是否发送 JSON body（整份 input），默认 true；</li>
 *     <li>{@code parameters} / {@code inputSchema}（可选）：JSON Schema 子集，供 {@link HttpProxyAgentTool} 向模型声明入参；
 *     未配置时等价于 {@code additionalProperties: true} 的宽松对象；</li>
 *     <li>{@code outputSchema}（可选）：JSON Schema 子集，描述返回体逻辑结构；运行时不校验真实输出，由
 *     {@link com.agentlego.backend.tool.schema.ToolOutputSchemaDescription} 写入工具 description 供模型理解（HTTP / WORKFLOW 等均适用）。</li>
 * </ul>
 */
public final class HttpToolSpec {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    private static final int MAX_URL_LENGTH = 4096;

    private final String urlTemplate;
    private final String method;
    private final Map<String, String> headers;
    private final boolean sendJsonBody;

    private HttpToolSpec(String urlTemplate, String method, Map<String, String> headers, boolean sendJsonBody) {
        this.urlTemplate = urlTemplate;
        this.method = method;
        this.headers = headers;
        this.sendJsonBody = sendJsonBody;
    }

    public static void validateDefinition(Map<String, Object> definition) {
        fromDefinition(definition);
    }

    public static HttpToolSpec fromDefinition(Map<String, Object> definition) {
        if (definition == null || definition.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "HTTP 工具定义不能为空", HttpStatus.BAD_REQUEST);
        }
        String url = stringField(definition, "url");
        if (url == null || url.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "HTTP 工具定义的 definition.url 为必填", HttpStatus.BAD_REQUEST);
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new ApiException("VALIDATION_ERROR", "definition.url 太长", HttpStatus.BAD_REQUEST);
        }
        String methodRaw = stringField(definition, "method");
        String method = (methodRaw == null || methodRaw.isBlank()) ? "GET" : methodRaw.trim().toUpperCase(Locale.ROOT);
        validateMethod(method);
        Map<String, String> headers = parseHeaders(definition.get("headers"));
        boolean sendJsonBody = boolField(definition, "sendJsonBody", !isMethodWithoutBody(method));
        return new HttpToolSpec(url.trim(), method, headers, sendJsonBody);
    }

    private static void validateMethod(String method) {
        switch (method) {
            case "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE" -> {
            }
            default -> throw new ApiException(
                    "VALIDATION_ERROR",
                    "不支持的 HTTP 方法：" + method,
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private static boolean isMethodWithoutBody(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method);
    }

    private static String stringField(Map<String, Object> def, String key) {
        Object v = def.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    private static boolean boolField(Map<String, Object> def, String key, boolean defaultValue) {
        Object v = def.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static Map<String, String> parseHeaders(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new ApiException("VALIDATION_ERROR", "definition.headers 必须是对象", HttpStatus.BAD_REQUEST);
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String k = String.valueOf(Objects.requireNonNull(e.getKey()));
            Object val = e.getValue();
            if (val == null) {
                continue;
            }
            out.put(k, String.valueOf(val));
        }
        return Collections.unmodifiableMap(out);
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public String resolveUrl(Map<String, Object> input) {
        Map<String, Object> in = input == null ? Map.of() : input;
        Matcher m = PLACEHOLDER.matcher(urlTemplate);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            Object val = in.get(key);
            String replacement = val == null ? "" : urlEncode(String.valueOf(val));
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        String resolved = sb.toString();
        if (resolved.length() > MAX_URL_LENGTH) {
            throw new ApiException("VALIDATION_ERROR", "解析后的 url 太长", HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public boolean isSendJsonBody() {
        return sendJsonBody;
    }
}
