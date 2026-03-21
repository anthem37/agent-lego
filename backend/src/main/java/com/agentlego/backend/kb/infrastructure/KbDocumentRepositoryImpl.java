package com.agentlego.backend.kb.infrastructure;

import com.agentlego.backend.kb.domain.KbDocumentRepository;
import com.agentlego.backend.kb.domain.KbDocumentRow;
import com.agentlego.backend.kb.infrastructure.persistence.KbDocumentDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbDocumentMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class KbDocumentRepositoryImpl implements KbDocumentRepository {

    private final KbDocumentMapper mapper;

    public KbDocumentRepositoryImpl(KbDocumentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertPending(String id, String collectionId, String title, String body) {
        KbDocumentDO row = new KbDocumentDO();
        row.setId(id);
        row.setCollectionId(collectionId);
        row.setTitle(title);
        row.setBody(body);
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
    public Optional<KbDocumentRow> findById(String id) {
        KbDocumentDO row = mapper.findById(id);
        return row == null ? Optional.empty() : Optional.of(toRow(row));
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

    private KbDocumentRow toRow(KbDocumentDO row) {
        KbDocumentRow r = new KbDocumentRow();
        r.setId(row.getId());
        r.setCollectionId(row.getCollectionId());
        r.setTitle(row.getTitle());
        r.setBody(row.getBody());
        r.setStatus(row.getStatus());
        r.setErrorMessage(row.getErrorMessage());
        r.setCreatedAt(row.getCreatedAt());
        r.setUpdatedAt(row.getUpdatedAt());
        return r;
    }
}
