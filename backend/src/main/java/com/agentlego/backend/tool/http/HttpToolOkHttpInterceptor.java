package com.agentlego.backend.tool.http;

import okhttp3.Interceptor;

/**
 * 挂载到「平台 HTTP 工具」专用 OkHttp 客户端的应用层拦截器 SPI。
 * <p>
 * 实现本接口并注册为 Spring Bean 即可参与构建 {@link OkHttpHttpToolRequestExecutor} 内的
 * {@link okhttp3.OkHttpClient}（使用 {@link okhttp3.OkHttpClient.Builder#addInterceptor}，便于日志、指标、脱敏等）。
 * <p>
 * 勿与业务中其它 OkHttp 实例混用；仅收集实现此接口的 Bean，避免误注入无关 {@link Interceptor}。
 */
public interface HttpToolOkHttpInterceptor extends Interceptor {
}
