package com.agentlego.backend.common;

/**
 * 跨应用层复用的异常文案（无业务语义）。
 */
public final class Throwables {

    private Throwables() {
    }

    /**
     * message 为空或空白时返回异常类简单名，便于落库与对外提示。
     */
    public static String messageOrSimpleName(Throwable t) {
        if (t == null) {
            return "Throwable";
        }
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }
}
