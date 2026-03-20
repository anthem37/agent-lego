package com.agentlego.backend.memory.application.assembler;

import com.agentlego.backend.memory.application.dto.MemoryItemDto;
import com.agentlego.backend.memory.domain.MemoryItemAggregate;

/**
 * 记忆条目聚合 → DTO。
 */
public final class MemoryAssembler {

    private MemoryAssembler() {
    }

    public static MemoryItemDto toDto(MemoryItemAggregate agg) {
        if (agg == null) {
            return null;
        }
        MemoryItemDto dto = new MemoryItemDto();
        dto.setId(agg.getId());
        dto.setOwnerScope(agg.getOwnerScope());
        dto.setContent(agg.getContent());
        dto.setMetadata(agg.getMetadata());
        dto.setCreatedAt(agg.getCreatedAt());
        return dto;
    }
}
