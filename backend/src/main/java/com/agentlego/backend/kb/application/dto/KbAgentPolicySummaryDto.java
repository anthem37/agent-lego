package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 知识库控制台：智能体 knowledge_base_policy 中的 collectionIds 摘要。
 */
@Data
public class KbAgentPolicySummaryDto {
    private String agentId;
    private String agentName;
    private List<String> collectionIds;
}
