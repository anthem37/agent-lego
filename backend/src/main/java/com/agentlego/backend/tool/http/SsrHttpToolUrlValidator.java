package com.agentlego.backend.tool.http;

import org.springframework.stereotype.Component;

/**
 * 生产环境默认策略：使用 {@link SsrUrlGuard} 防止 SSRF。
 */
@Component
public final class SsrHttpToolUrlValidator implements HttpToolUrlValidator {

    @Override
    public void validate(String url) {
        SsrUrlGuard.validate(url);
    }
}
