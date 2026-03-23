package com.agentlego.backend.tool.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 工具被智能体 / 知识库文档引用情况（用于删除前提示）。
 */
@Data
public class ToolReferencesDto {
    /**
     * 引用该工具（toolIds 含此工具记录 id）的智能体数量。
     */
    private int referencingAgentCount;
    /**
     * 部分引用方智能体 id（最多若干条，按创建时间倒序）。
     */
    private List<String> referencingAgentIds;
    /**
     * {@code linked_tool_ids} 绑定中含该工具 id 的知识库文档数量。
     */
    private long referencingKbDocumentCount;
}
