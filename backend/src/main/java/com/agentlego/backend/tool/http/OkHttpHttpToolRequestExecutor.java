package com.agentlego.backend.tool.http;

import com.agentlego.backend.api.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 使用 Square <a href="https://square.github.io/okhttp/">OkHttp</a> 执行 HTTP 工具请求（替代 {@code java.net.http.*}）。
 * <p>
 * 行为对齐原实现：不跟随重试/重定向、默认 HTTP/1.1、响应体长度上限截断；URL 安全策略由 {@link HttpToolUrlValidator} 注入（生产环境为
 * {@link SsrHttpToolUrlValidator}）。可通过实现 {@link HttpToolOkHttpInterceptor} 并注册 Bean 扩展拦截器链。
 */
@Component
public final class OkHttpHttpToolRequestExecutor implements HttpToolRequestExecutor {

    private static final int MAX_RESPONSE_CHARS = 256 * 1024;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final RequestBody EMPTY_ENTITY = RequestBody.create(new byte[0], null);

    private final OkHttpClient client;
    private final HttpToolUrlValidator urlValidator;

    @Autowired
    public OkHttpHttpToolRequestExecutor(
            @Value("${agentlego.tool.http-call-timeout-seconds:120}") int callTimeoutSeconds,
            @Value("${agentlego.tool.http-connect-timeout-seconds:10}") int connectTimeoutSeconds,
            HttpToolUrlValidator urlValidator,
            @Autowired(required = false) List<HttpToolOkHttpInterceptor> httpToolOkHttpInterceptors
    ) {
        this.urlValidator = Objects.requireNonNull(urlValidator, "urlValidator");
        int callSec = Math.max(5, Math.min(callTimeoutSeconds, 600));
        int connSec = Math.max(1, Math.min(connectTimeoutSeconds, 120));
        List<HttpToolOkHttpInterceptor> ic = httpToolOkHttpInterceptors == null ? List.of() : httpToolOkHttpInterceptors;
        this.client = buildClient(callSec, connSec, ic);
    }

    private OkHttpHttpToolRequestExecutor(OkHttpClient client, HttpToolUrlValidator urlValidator) {
        this.client = Objects.requireNonNull(client, "client");
        this.urlValidator = Objects.requireNonNull(urlValidator, "urlValidator");
    }

    /**
     * 与同包单测使用：不经过 Spring 属性注入。
     */
    static OkHttpHttpToolRequestExecutor forTest(
            int callTimeoutSeconds,
            int connectTimeoutSeconds,
            HttpToolUrlValidator urlValidator,
            List<HttpToolOkHttpInterceptor> interceptors
    ) {
        int callSec = Math.max(5, Math.min(callTimeoutSeconds, 600));
        int connSec = Math.max(1, Math.min(connectTimeoutSeconds, 120));
        List<HttpToolOkHttpInterceptor> ic = interceptors == null ? List.of() : interceptors;
        return new OkHttpHttpToolRequestExecutor(buildClient(callSec, connSec, ic), urlValidator);
    }

    private static OkHttpClient buildClient(int callSec, int connSec, List<HttpToolOkHttpInterceptor> interceptors) {
        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .connectTimeout(connSec, TimeUnit.SECONDS)
                .readTimeout(callSec, TimeUnit.SECONDS)
                .writeTimeout(callSec, TimeUnit.SECONDS)
                .callTimeout(callSec, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1));
        for (HttpToolOkHttpInterceptor i : interceptors) {
            b.addInterceptor(Objects.requireNonNull(i, "httpToolOkHttpInterceptor"));
        }
        return b.build();
    }

    private static boolean wantsEntityBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private static boolean hasContentTypeHeader(HttpToolSpec spec) {
        return spec.getHeaders().keySet().stream().anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
    }

    private static Request buildRequest(HttpUrl url, String method, HttpToolSpec spec, byte[] jsonBodyBytes) {
        Request.Builder rb = new Request.Builder().url(url);
        for (Map.Entry<String, String> h : spec.getHeaders().entrySet()) {
            rb.header(h.getKey(), h.getValue());
        }
        if (spec.isSendJsonBody() && wantsEntityBody(method) && !hasContentTypeHeader(spec)) {
            rb.header("Content-Type", "application/json; charset=UTF-8");
        }

        return switch (method) {
            case "GET" -> rb.get().build();
            case "HEAD" -> rb.head().build();
            case "DELETE" -> rb.delete().build();
            case "POST" -> rb.post(entityBody(jsonBodyBytes)).build();
            case "PUT" -> rb.put(entityBody(jsonBodyBytes)).build();
            case "PATCH" -> rb.patch(entityBody(jsonBodyBytes)).build();
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }

    private static RequestBody entityBody(byte[] jsonBodyBytes) {
        if (jsonBodyBytes != null) {
            return RequestBody.create(jsonBodyBytes, JSON_MEDIA_TYPE);
        }
        return EMPTY_ENTITY;
    }

    @Override
    public HttpToolExecutionResult execute(HttpToolSpec spec, Map<String, Object> input, ObjectMapper objectMapper) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(objectMapper, "objectMapper");
        Map<String, Object> in = input == null ? Map.of() : input;

        final String resolvedUrl;
        try {
            resolvedUrl = spec.resolveUrl(in);
            urlValidator.validate(resolvedUrl);
        } catch (ApiException e) {
            return new HttpToolExecutionResult.Failure(e.getMessage());
        }

        HttpUrl httpUrl = HttpUrl.parse(resolvedUrl);
        if (httpUrl == null) {
            return new HttpToolExecutionResult.Failure("Invalid URL: " + resolvedUrl);
        }

        String method = spec.getMethod().toUpperCase(Locale.ROOT);
        byte[] jsonBodyBytes = null;
        if (spec.isSendJsonBody() && wantsEntityBody(method)) {
            try {
                jsonBodyBytes = objectMapper.writeValueAsBytes(in);
            } catch (JsonProcessingException e) {
                return new HttpToolExecutionResult.Failure("Failed to serialize JSON body: " + e.getMessage());
            }
        }

        Request request = buildRequest(httpUrl, method, spec, jsonBodyBytes);

        try (Response response = client.newCall(request).execute()) {
            String bodyText = "";
            ResponseBody body = response.body();
            if (body != null) {
                bodyText = body.string();
            }
            if (bodyText.length() > MAX_RESPONSE_CHARS) {
                bodyText = bodyText.substring(0, MAX_RESPONSE_CHARS) + "\n...[truncated]";
            }
            String contentType = response.header("Content-Type");
            return new HttpToolExecutionResult.Success(
                    response.code(),
                    bodyText,
                    contentType,
                    resolvedUrl,
                    method
            );
        } catch (IOException e) {
            return new HttpToolExecutionResult.Failure("HTTP request failed: " + e.getMessage());
        }
    }
}
