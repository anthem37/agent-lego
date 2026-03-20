package com.agentlego.backend.tool.http;

import com.agentlego.backend.api.ApiException;
import org.springframework.http.HttpStatus;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * 防止 HTTP 工具被用于 SSRF：拦截内网、回环与常见元数据地址。
 */
public final class SsrUrlGuard {

    private SsrUrlGuard() {
    }

    public static void validate(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "url 为空", HttpStatus.BAD_REQUEST);
        }
        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", "无效的 url：" + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
            throw new ApiException("VALIDATION_ERROR", "仅允许 http/https URL", HttpStatus.BAD_REQUEST);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "url 必须包含 host", HttpStatus.BAD_REQUEST);
        }
        if (isBlockedHostLiteral(host)) {
            throw new ApiException("VALIDATION_ERROR", "目标 host 不允许：" + host, HttpStatus.BAD_REQUEST);
        }
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress()
                    || addr.isAnyLocalAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()) {
                throw new ApiException(
                        "VALIDATION_ERROR",
                        "目标 host 解析到被禁止的地址（本地/内网/私网）",
                        HttpStatus.BAD_REQUEST
                );
            }
        } catch (UnknownHostException e) {
            throw new ApiException("VALIDATION_ERROR", "无法解析 host：" + host, HttpStatus.BAD_REQUEST);
        }
    }

    private static boolean isBlockedHostLiteral(String host) {
        String h = host.toLowerCase();
        if ("169.254.169.254".equals(h)) {
            return true;
        }
        return h.endsWith(".local") || h.contains("metadata.google.internal");
    }
}
