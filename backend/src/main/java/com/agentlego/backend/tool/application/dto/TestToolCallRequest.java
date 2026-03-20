package com.agentlego.backend.tool.application.dto;

import lombok.Data;

import java.util.Map;

@Data
public class TestToolCallRequest {
    private Map<String, Object> input;
}

