package com.agentlego.backend.api;

import org.springframework.http.HttpStatus;

/**
 * 应用层入参断言（共享内核）：将「非空字符串」校验集中在一处，避免各 {@code *ApplicationService} 重复实现。
 * <p>
 * 仅抛出 {@link ApiException}，不依赖 Web 层，符合 DDD 中应用服务编排边界。
 */
public final class ApiRequires {

    private ApiRequires() {
    }

    /**
     * @return 原样返回 {@code value}（调用方可再 {@code trim()} 等处理）
     */
    public static String nonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", fieldName + " 为必填", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    /**
     * trim 后非空，错误文案与知识库召回等接口一致（「xxx 不能为空」）。
     */
    public static String nonBlankTrimmed(String value, String fieldName) {
        String q = value == null ? "" : value.trim();
        if (q.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", fieldName + " 不能为空", HttpStatus.BAD_REQUEST);
        }
        return q;
    }
}
