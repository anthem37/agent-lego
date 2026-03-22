package com.agentlego.backend.kb.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KbRetrievePreviewHitDto {
    private String chunkId;
    /**
     * 命中文档所属集合；单集合预览时也会填充，便于与多集合结果统一展示
     */
    private String collectionId;
    private String collectionName;
    private String documentId;
    private String documentTitle;
    private double score;
    /**
     * 分片原始正文（可能较长，由服务端限制最大长度）
     */
    private String content;
    /**
     * {@code renderSnippets=true} 时填充
     */
    private String renderedContent;
    /**
     * 从分片正文中解析出的「相似问」行（与入库时拼入 embedding 的块一致），便于召回结果回显。
     */
    private List<String> similarQueries;
}
