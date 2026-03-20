package com.agentlego.backend.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 统一 API 返回结构。
 * <p>
 * 字段约定：
 * - code：业务码（不是 HTTP Status）
 * - message：面向调用方的简短语义信息（success/created/错误原因等）
 * - data：业务数据
 * - traceId：用于日志串联排查（当前为进程内随机生成，后续可对接分布式 tracing）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    /**
     * 业务码（例如 OK / NOT_FOUND / VALIDATION_ERROR）。
     */
    private String code;
    /**
     * 提示信息（简短、稳定；便于前端/调用方判断）。
     */
    private String message;
    /**
     * 响应数据载荷。
     */
    private T data;
    /**
     * 请求链路追踪 ID（traceId）。
     */
    private String traceId;

    /**
     * 成功响应。
     * <p>
     * - traceId：用于日志串联排查（当前为进程内随机生成，尚未接入分布式链路追踪）。
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data, UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    /**
     * 创建成功响应（通常配合 HTTP 201）。
     */
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>("OK", "created", data, UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, UUID.randomUUID().toString());
    }
}

