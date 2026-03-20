package com.agentlego.backend.common;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 共享 Jackson ObjectMapper，避免各处重复 {@code new ObjectMapper()}。
 * <p>
 * ObjectMapper 线程安全，可全局复用。
 */
public final class JacksonHolder {
    public static final ObjectMapper INSTANCE = new ObjectMapper();

    private JacksonHolder() {
    }
}
