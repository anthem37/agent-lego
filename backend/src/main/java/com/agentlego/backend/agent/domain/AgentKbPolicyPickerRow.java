package com.agentlego.backend.agent.domain;

/**
 * 控制台知识库：下拉选择智能体时仅需 id、名称与策略 JSON 文本。
 */
public record AgentKbPolicyPickerRow(String id, String name, String knowledgeBasePolicyJson) {
}
