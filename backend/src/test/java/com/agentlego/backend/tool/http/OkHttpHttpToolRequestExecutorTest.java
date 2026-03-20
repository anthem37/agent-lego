package com.agentlego.backend.tool.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用 OkHttp {@link MockWebServer} 验证执行器（单测中跳过 SSRF，避免 127.0.0.1 被 {@link SsrUrlGuard} 拦截）。
 */
class OkHttpHttpToolRequestExecutorTest {

    private MockWebServer server;
    private OkHttpHttpToolRequestExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        executor = OkHttpHttpToolRequestExecutor.forTest(30, 5, url -> {
            /* 单测仅验证 HTTP 栈，不重复测 SsrUrlGuard */
        }, List.of());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void get_shouldReturnBodyAndMeta() {
        server.enqueue(new MockResponse().setBody("hello").setHeader("Content-Type", "text/plain"));

        HttpToolSpec spec = HttpToolSpec.fromDefinition(Map.of(
                "url", server.url("/p").toString(),
                "method", "GET"
        ));

        HttpToolExecutionResult r = executor.execute(spec, Map.of(), new ObjectMapper());
        assertInstanceOf(HttpToolExecutionResult.Success.class, r);
        HttpToolExecutionResult.Success s = (HttpToolExecutionResult.Success) r;
        assertEquals(200, s.statusCode());
        assertEquals("hello", s.body());
        assertTrue(Objects.requireNonNull(s.contentType()).contains("text/plain"));
        assertEquals("GET", s.method());
    }

    @Test
    void postJson_shouldSendBody() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"ok\":true}").setHeader("Content-Type", "application/json"));

        HttpToolSpec spec = HttpToolSpec.fromDefinition(Map.of(
                "url", server.url("/api").toString(),
                "method", "POST",
                "sendJsonBody", true
        ));

        HttpToolExecutionResult r = executor.execute(spec, Map.of("a", 1), new ObjectMapper());
        assertInstanceOf(HttpToolExecutionResult.Success.class, r);

        var req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertNotNull(req.getBody());
        assertTrue(req.getPath().startsWith("/api"));
    }

    @Test
    void httpToolOkHttpInterceptor_shouldRunBeforeRequest() throws Exception {
        HttpToolOkHttpInterceptor tag = new HttpToolOkHttpInterceptor() {
            @Override
            public Response intercept(Interceptor.Chain chain) throws IOException {
                return chain.proceed(
                        chain.request().newBuilder().header("X-Agent-Lego-Test", "probe").build()
                );
            }
        };
        OkHttpHttpToolRequestExecutor exec = OkHttpHttpToolRequestExecutor.forTest(30, 5, url -> {
        }, List.of(tag));

        server.enqueue(new MockResponse().setBody("ok"));

        HttpToolSpec spec = HttpToolSpec.fromDefinition(Map.of(
                "url", server.url("/tagged").toString(),
                "method", "GET"
        ));
        assertInstanceOf(HttpToolExecutionResult.Success.class, exec.execute(spec, Map.of(), new ObjectMapper()));

        var req = server.takeRequest();
        assertEquals("probe", req.getHeader("X-Agent-Lego-Test"));
    }
}
