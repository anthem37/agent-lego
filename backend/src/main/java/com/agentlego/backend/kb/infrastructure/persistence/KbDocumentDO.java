package com.agentlego.backend.kb.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class KbDocumentDO {
    private String id;
    private String kbKey;
    private String name;
    private Instant createdAt;
}

