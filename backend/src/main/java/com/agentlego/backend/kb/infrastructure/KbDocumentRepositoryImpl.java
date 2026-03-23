package com.agentlego.backend.kb.infrastructure;

import com.agentlego.backend.kb.domain.KbDocumentRepository;
import com.agentlego.backend.kb.domain.KbDocumentRow;
import com.agentlego.backend.kb.infrastructure.persistence.KbDocumentDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbDocumentMapper;
import com.agentlego.backend.kb.support.KbDocumentToolBindings;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Repository
public class KbDocumentRepositoryImpl implements KbDocumentRepository {

    private final KbDocumentMapper mapper;

    public KbDocumentRepositoryImpl(KbDocumentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertPending(
            String id,
            String collectionId,
            String title,
            String body,
            String bodyRich,
            String linkedToolIdsJson,
            String toolOutputBindingsJson,
            String similarQueriesJson
    ) {
        KbDocumentDO row = new KbDocumentDO();
        row.setId(id);
        row.setCollectionId(collectionId);
        row.setTitle(title);
        row.setBody(body);
        row.setBodyRich(bodyRich == null || bodyRich.isBlank() ? null : bodyRich);
        row.setLinkedToolIdsJson(
                linkedToolIdsJson == null || linkedToolIdsJson.isBlank() ? "[]" : linkedToolIdsJson
        );
        row.setToolOutputBindingsJson(
                toolOutputBindingsJson == null || toolOutputBindingsJson.isBlank()
                        ? KbDocumentToolBindings.defaultBindingsJson()
                        : toolOutputBindingsJson
        );
        row.setSimilarQueriesJson(
                similarQueriesJson == null || similarQueriesJson.isBlank() ? "[]" : similarQueriesJson
        );
        row.setCreatedAt(java.time.Instant.now());
        row.setUpdatedAt(row.getCreatedAt());
        mapper.insertPending(row);
    }

    @Override
    public void markReady(String id) {
        mapper.updateStatus(id, "READY", null);
    }

    @Override
    public void markFailed(String id, String errorMessage) {
        String msg = errorMessage == null ? "unknown" : errorMessage;
        if (msg.length() > 4000) {
            msg = msg.substring(0, 4000);
        }
        mapper.updateStatus(id, "FAILED", msg);
    }

    @Override
    public void updateReingest(
            String id,
            String title,
            String body,
            String bodyRich,
            String linkedToolIdsJson,
            String toolOutputBindingsJson,
            String similarQueriesJson
    ) {
        String linked = linkedToolIdsJson == null || linkedToolIdsJson.isBlank() ? "[]" : linkedToolIdsJson;
        String bindings = toolOutputBindingsJson == null || toolOutputBindingsJson.isBlank()
                ? KbDocumentToolBindings.defaultBindingsJson()
                : toolOutputBindingsJson;
        String sq = similarQueriesJson == null || similarQueriesJson.isBlank() ? "[]" : similarQueriesJson;
        mapper.updateReingest(
                id,
                title,
                body,
                bodyRich == null || bodyRich.isBlank() ? null : bodyRich,
                linked,
                bindings,
                sq
        );
    }

    @Override
    public Optional<KbDocumentRow> findById(String id) {
        KbDocumentDO row = mapper.findById(id);
        return row == null ? Optional.empty() : Optional.of(toRow(row));
    }

    @Override
    public List<KbDocumentRow> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                distinct.add(id.trim());
            }
        }
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<KbDocumentDO> rows = mapper.findByIds(new ArrayList<>(distinct));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::toRow).toList();
    }

    @Override
    public List<KbDocumentRow> listByCollectionId(String collectionId) {
        List<KbDocumentDO> rows = mapper.listByCollectionId(collectionId);
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(this::toRow).toList();
    }

    @Override
    public void deleteById(String id) {
        mapper.deleteById(id);
    }

    @Override
    public long countDocumentsReferencingToolId(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return 0L;
        }
        return mapper.countDocumentsReferencingToolId(toolId.trim());
    }

    private KbDocumentRow toRow(KbDocumentDO row) {
        KbDocumentRow r = new KbDocumentRow();
        r.setId(row.getId());
        r.setCollectionId(row.getCollectionId());
        r.setTitle(row.getTitle());
        r.setBody(row.getBody());
        r.setBodyRich(row.getBodyRich());
        r.setStatus(row.getStatus());
        r.setErrorMessage(row.getErrorMessage());
        r.setLinkedToolIdsJson(
                row.getLinkedToolIdsJson() == null || row.getLinkedToolIdsJson().isBlank()
                        ? "[]"
                        : row.getLinkedToolIdsJson()
        );
        r.setToolOutputBindingsJson(
                row.getToolOutputBindingsJson() == null || row.getToolOutputBindingsJson().isBlank()
                        ? KbDocumentToolBindings.defaultBindingsJson()
                        : row.getToolOutputBindingsJson()
        );
        r.setSimilarQueriesJson(
                row.getSimilarQueriesJson() == null || row.getSimilarQueriesJson().isBlank()
                        ? "[]"
                        : row.getSimilarQueriesJson()
        );
        r.setCreatedAt(row.getCreatedAt());
        r.setUpdatedAt(row.getUpdatedAt());
        return r;
    }
}
