package com.agentlego.backend.tool.http;

import com.agentlego.backend.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsrUrlGuardTest {

    @Test
    void shouldRejectLoopback() {
        assertThrows(ApiException.class, () -> SsrUrlGuard.validate("http://127.0.0.1/api"));
    }

    @Test
    void shouldRejectNonHttpScheme() {
        assertThrows(ApiException.class, () -> SsrUrlGuard.validate("ftp://example.com/x"));
    }

    @Test
    void shouldAcceptPublicHttpUrl() {
        assertDoesNotThrow(() -> SsrUrlGuard.validate("https://example.com/path"));
    }
}
