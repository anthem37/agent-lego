package com.agentlego.backend.agent.infrastructure.persistence;

/**
 * MyBatis 映射：{@link com.agentlego.backend.agent.infrastructure.persistence.AgentMapper#listAgentToolReferencesWithCount} 单行。
 */
public class AgentToolRefRow {
    private String agentId;
    private int totalCount;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
