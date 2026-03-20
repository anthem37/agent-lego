package com.agentlego.backend.tool.http;

import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 可选：为 HTTP 工具出站请求打结构化日志（默认关闭，避免泄露 URL 查询参数中的敏感信息）。
 * <p>
 * 开启：<code>agentlego.tool.http-request-logging=true</code>。仅记录 method、host、path（不含 query）、状态码与耗时。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "agentlego.tool.http-request-logging", havingValue = "true")
public final class Slf4jHttpToolOkHttpInterceptor implements HttpToolOkHttpInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Slf4jHttpToolOkHttpInterceptor.class);

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        long t0 = System.nanoTime();
        try {
            Response response = chain.proceed(request);
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            if (log.isInfoEnabled()) {
                log.info(
                        "http-tool {} {}{} -> {} in {} ms",
                        request.method(),
                        request.url().host(),
                        request.url().encodedPath(),
                        response.code(),
                        ms
                );
            }
            return response;
        } catch (IOException e) {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            log.warn(
                    "http-tool {} {}{} failed after {} ms: {}",
                    request.method(),
                    request.url().host(),
                    request.url().encodedPath(),
                    ms,
                    e.toString()
            );
            throw e;
        }
    }
}
