package com.agentlego.backend.kb.support;

import com.agentlego.backend.kb.application.dto.KbRetrievePreviewHitDto;
import com.agentlego.backend.kb.application.dto.KbRetrievePreviewResponse;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbDocumentRepository;
import com.agentlego.backend.kb.domain.KbDocumentRow;
import com.agentlego.backend.kb.rag.KbRagRankedChunk;
import com.agentlego.backend.kb.rag.KbRetrievedChunkRenderer;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 将向量检索得到的 {@link KbRagRankedChunk} 组装为控制台召回预览 DTO（含可选片段渲染与相似问解析）。
 */
@Component
public class KbRetrievePreviewAssembler {

    private final KbDocumentRepository documentRepository;
    private final KbRetrievedChunkRenderer retrievedChunkRenderer;

    public KbRetrievePreviewAssembler(
            KbDocumentRepository documentRepository,
            KbRetrievedChunkRenderer retrievedChunkRenderer
    ) {
        this.documentRepository = documentRepository;
        this.retrievedChunkRenderer = retrievedChunkRenderer;
    }

    private static String truncatePreviewText(String raw) {
        if (raw == null || raw.length() <= KbLimits.PREVIEW_MAX_CONTENT_CHARS) {
            return raw == null ? "" : raw;
        }
        return raw.substring(0, KbLimits.PREVIEW_MAX_CONTENT_CHARS) + "\n…(已截断)";
    }

    public KbRetrievePreviewResponse assemble(
            String query,
            List<KbRagRankedChunk> ranked,
            boolean renderSnippets,
            Map<String, KbCollectionAggregate> colById
    ) {
        LinkedHashSet<String> docIds = new LinkedHashSet<>();
        for (KbRagRankedChunk c : ranked) {
            if (c.documentId() != null && !c.documentId().isBlank()) {
                docIds.add(c.documentId().trim());
            }
        }
        Map<String, KbDocumentRow> docById = new LinkedHashMap<>();
        if (!docIds.isEmpty()) {
            for (KbDocumentRow row : documentRepository.findByIds(new ArrayList<>(docIds))) {
                if (row != null && row.getId() != null) {
                    docById.put(row.getId(), row);
                }
            }
        }
        List<KbRetrievePreviewHitDto> hits = new ArrayList<>();
        for (KbRagRankedChunk c : ranked) {
            String raw = c.content() == null ? "" : c.content();
            String content = truncatePreviewText(raw);
            KbDocumentRow drow = c.documentId() == null ? null : docById.get(c.documentId().trim());
            String title = drow == null ? "" : (drow.getTitle() == null ? "" : drow.getTitle());
            String cid = "";
            String cname = "";
            if (drow != null && drow.getCollectionId() != null && !drow.getCollectionId().isBlank()) {
                cid = drow.getCollectionId().trim();
                KbCollectionAggregate ca = colById.get(cid);
                if (ca != null && ca.getName() != null) {
                    cname = ca.getName();
                }
            }
            String rendered = null;
            if (renderSnippets && drow != null) {
                rendered = truncatePreviewText(retrievedChunkRenderer.renderForModel(raw, drow, Map.of()));
            }
            hits.add(
                    new KbRetrievePreviewHitDto(
                            c.chunkId(),
                            cid.isEmpty() ? null : cid,
                            cname.isEmpty() ? null : cname,
                            c.documentId() == null ? "" : c.documentId(),
                            title,
                            c.score(),
                            content,
                            rendered,
                            KbChunkSimilarQueries.parseFromChunkText(raw)));
        }
        KbRetrievePreviewResponse out = new KbRetrievePreviewResponse();
        out.setQuery(query);
        out.setHits(hits);
        return out;
    }
}
