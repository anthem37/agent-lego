package com.agentlego.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 全局异常处理。
 * <p>
 * 响应体直接写入 {@link HttpServletResponse}，避免依赖内容协商（Accept）。
 * MCP / SSE 等请求的 <code>Accept: text/event-stream</code> 会导致
 * <code>ResponseEntity + MappingJackson2HttpMessageConverter</code> 抛出
 * {@link org.springframework.web.HttpMediaTypeNotAcceptableException}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(ApiException.class)
    public void handleApiException(ApiException ex, HttpServletResponse response) throws IOException {
        writeJson(response, ex.getStatus().value(), ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidation(MethodArgumentNotValidException ex, HttpServletResponse response) throws IOException {
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        String zh = message.isBlank() ? "参数校验未通过" : "参数校验未通过：" + message;
        writeJson(response, HttpStatus.BAD_REQUEST.value(), ApiResponse.error("VALIDATION_ERROR", zh));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public void handleUnreadableJson(HttpMessageNotReadableException ex, HttpServletResponse response) throws IOException {
        writeJson(
                response,
                HttpStatus.BAD_REQUEST.value(),
                ApiResponse.error("VALIDATION_ERROR", "请求体不能为空或 JSON 无效")
        );
    }

    @ExceptionHandler(Exception.class)
    public void handleUnknown(Exception ex, HttpServletResponse response) throws IOException {
        log.warn("Unhandled exception", ex);
        writeJson(
                response,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ApiResponse.error("INTERNAL_ERROR", "服务器内部错误")
        );
    }

    private void writeJson(HttpServletResponse response, int httpStatus, ApiResponse<?> body) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
