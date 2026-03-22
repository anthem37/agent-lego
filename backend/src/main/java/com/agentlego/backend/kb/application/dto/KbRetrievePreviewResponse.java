package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KbRetrievePreviewResponse {
    private String query;
    private List<KbRetrievePreviewHitDto> hits = new ArrayList<>();
}
