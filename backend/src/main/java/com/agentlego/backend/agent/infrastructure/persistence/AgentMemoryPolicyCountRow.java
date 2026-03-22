package com.agentlego.backend.agent.infrastructure.persistence;

import lombok.Data;

@Data
public class AgentMemoryPolicyCountRow {
    private String policyId;
    private Integer cnt;
}
