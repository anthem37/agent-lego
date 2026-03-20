package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.util.List;

@Data
public class KbQueryResponse {
    private List<KbChunkDto> chunks;
}

