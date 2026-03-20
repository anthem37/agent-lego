package com.agentlego.backend.kb.application.dto;

import lombok.Data;

@Data
public class KbIngestResponse {
    private String documentId;
    /** 本次写入产生的分片数量 */
    private int chunkCount;
}

