package com.agentlego.backend.kb.application.dto;

/**
 * 删除知识库集合的附带结果：已从多少智能体的 {@code knowledge_base_policy} 中移除该集合。
 */
public record KbCollectionDeleteResult(int agentsPolicyUpdated) {
}
