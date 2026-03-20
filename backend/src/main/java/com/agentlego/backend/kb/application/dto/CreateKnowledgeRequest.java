package com.agentlego.backend.kb.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 向指定知识库添加「知识」文档（Markdown 或 HTML 富文本 + 分片参数）。
 * <p>检索分片基于纯文本：{@code html} 去标签；{@code markdown} 先渲染为 HTML 再去标签。</p>
 */
@Data
public class CreateKnowledgeRequest {
    @NotBlank
    private String name;

    @NotBlank
    @Size(max = 500_000)
    private String content;

    /**
     * markdown：正文为 MD 源码；html：正文为 HTML（大小写不敏感，缺省 markdown）。
     */
    private String contentFormat = "markdown";

    /**
     * 分片策略：fixed | paragraph | hybrid | markdown_sections（大小写不敏感，缺省 fixed）。
     * markdown_sections 在非 Markdown 正文时回退为 hybrid。
     */
    private String chunkStrategy = "fixed";

    @Min(100)
    private int chunkSize = 800;

    @Min(0)
    private int overlap = 100;
}
