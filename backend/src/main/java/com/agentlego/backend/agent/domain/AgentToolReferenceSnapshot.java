package com.agentlego.backend.agent.domain;

import java.util.List;

/**
 * 引用某平台工具的智能体统计（总数 + 样本 id，样本上限见仓储实现）。
 */
public record AgentToolReferenceSnapshot(int totalReferencingAgents, List<String> sampleAgentIds) {
}
