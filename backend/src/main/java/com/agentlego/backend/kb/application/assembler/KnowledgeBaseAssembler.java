package com.agentlego.backend.kb.application.assembler;

import com.agentlego.backend.kb.application.dto.KbBaseDto;
import com.agentlego.backend.kb.application.dto.KbChunkDto;
import com.agentlego.backend.kb.application.dto.KbDocumentSummaryDto;
import com.agentlego.backend.kb.application.dto.KbKnowledgeDetailDto;
import com.agentlego.backend.kb.domain.KbBaseSummary;
import com.agentlego.backend.kb.domain.KbChunkAggregate;
import com.agentlego.backend.kb.domain.KbDocumentDetail;
import com.agentlego.backend.kb.domain.KbDocumentSummary;

/**
 * 知识库领域读模型 / 聚合 → API DTO。
 */
public final class KnowledgeBaseAssembler {

    private KnowledgeBaseAssembler() {
    }

    public static KbChunkDto toChunkDto(KbChunkAggregate agg) {
        if (agg == null) {
            return null;
        }
        KbChunkDto dto = new KbChunkDto();
        dto.setId(agg.getId());
        dto.setDocumentId(agg.getDocumentId());
        dto.setDocumentName(agg.getDocumentName());
        dto.setChunkIndex(agg.getChunkIndex());
        dto.setContent(agg.getContent());
        dto.setMetadata(agg.getMetadata());
        dto.setCreatedAt(agg.getCreatedAt());
        return dto;
    }

    public static KbBaseDto toBaseDto(KbBaseSummary s) {
        if (s == null) {
            return null;
        }
        KbBaseDto dto = new KbBaseDto();
        dto.setId(s.getId());
        dto.setKbKey(s.getKbKey());
        dto.setName(s.getName());
        dto.setDescription(s.getDescription());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setDocumentCount(s.getDocumentCount());
        dto.setLastIngestAt(s.getLastIngestAt());
        return dto;
    }

    public static KbDocumentSummaryDto toDocumentDto(KbDocumentSummary s) {
        if (s == null) {
            return null;
        }
        KbDocumentSummaryDto dto = new KbDocumentSummaryDto();
        dto.setId(s.getId());
        dto.setBaseId(s.getBaseId());
        dto.setKbKey(s.getKbKey());
        dto.setName(s.getName());
        dto.setContentFormat(s.getContentFormat());
        dto.setChunkStrategy(s.getChunkStrategy());
        dto.setChunkCount(s.getChunkCount());
        dto.setCreatedAt(s.getCreatedAt());
        return dto;
    }

    public static KbKnowledgeDetailDto toKnowledgeDetailDto(KbDocumentDetail d) {
        if (d == null) {
            return null;
        }
        KbKnowledgeDetailDto dto = new KbKnowledgeDetailDto();
        dto.setId(d.getId());
        dto.setBaseId(d.getBaseId());
        dto.setKbKey(d.getKbKey());
        dto.setName(d.getName());
        dto.setContentRich(d.getContentRich());
        dto.setContentFormat(d.getContentFormat());
        dto.setChunkStrategy(d.getChunkStrategy());
        dto.setCreatedAt(d.getCreatedAt());
        return dto;
    }
}
