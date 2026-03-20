package com.agentlego.backend.agent.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class AgentDO {
    private String id;
    private String name;
    private String systemPrompt;
    private String modelId;
    private String toolIdsCsv;
    private String memoryPolicyJson;
    private String knowledgeBasePolicyJson;
    private Instant createdAt;
}

