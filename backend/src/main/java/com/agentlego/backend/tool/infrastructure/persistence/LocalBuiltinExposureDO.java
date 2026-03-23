package com.agentlego.backend.tool.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class LocalBuiltinExposureDO {
    private String toolName;
    private boolean exposeMcp;
    private boolean showInUi;
    private Instant updatedAt;
}
