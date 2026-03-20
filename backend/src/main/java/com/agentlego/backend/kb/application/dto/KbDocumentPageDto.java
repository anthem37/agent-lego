package com.agentlego.backend.kb.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KbDocumentPageDto {
    private List<KbDocumentSummaryDto> items;
    private long total;
    private int page;
    private int pageSize;
}
