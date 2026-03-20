package com.agentlego.backend.model.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 更新模型请求 DTO。
 * <p>
 * 合并规则（与常见「部分更新」约定一致）：
 * <ul>
 *     <li>{@code name}：为 {@code null} 表示不修改；非空则更新显示名称；</li>
 *     <li>{@code modelKey}：必填，写入新的模型标识；</li>
 *     <li>{@code baseUrl}：为 {@code null} 表示不修改；传空字符串表示清空；</li>
 *     <li>{@code description}：为 {@code null} 不修改；空字符串表示清空；</li>
 *     <li>{@code config}：为 {@code null} 表示不修改；传空对象表示清空默认推理参数；</li>
 *     <li>{@code apiKey}：为 {@code null} 表示不修改密钥；传空字符串表示清空（仅联调场景）。</li>
 * </ul>
 */
@Data
public class UpdateModelRequest {

    /**
     * 配置实例显示名称（可选；{@code null} 表示不修改）。
     */
    private String name;

    /**
     * provider 内部模型标识（必填）。
     */
    @NotBlank
    private String modelKey;

    /**
     * 模型服务 base URL（可选，语义见类注释）。
     */
    private String baseUrl;

    /**
     * 备注（可选；{@code null} 不修改；空字符串表示清空）。
     */
    private String description;

    /**
     * 默认推理参数（可选，语义见类注释）。
     */
    private Map<String, Object> config;

    /**
     * API Key（可选，语义见类注释）。
     */
    private String apiKey;
}
