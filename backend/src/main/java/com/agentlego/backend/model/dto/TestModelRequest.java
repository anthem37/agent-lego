package com.agentlego.backend.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模型连通性 / 能力测试的可选入参（POST body 可空）。
 */
@Data
public class TestModelRequest {

    /**
     * 用户输入：聊天为 user 消息文本；Embedding 为待向量化的单段文本。
     */
    @Size(max = 8000, message = "prompt 最长 8000 字符")
    private String prompt;

    /**
     * 覆盖本次测试的 maxTokens（仅聊天模型；为空则用服务端默认）。
     */
    @Min(value = 1, message = "maxTokens 至少为 1")
    @Max(value = 8192, message = "maxTokens 不超过 8192")
    private Integer maxTokens;

    /**
     * 流式响应最多采集的 chunk 数（仅聊天；用于观察流式是否连续）。
     */
    @Min(value = 1, message = "maxStreamChunks 至少为 1")
    @Max(value = 128, message = "maxStreamChunks 不超过 128")
    private Integer maxStreamChunks;
}
