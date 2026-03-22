package com.agentlego.backend.kb.milvus;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbVectorStoreKind;
import com.agentlego.backend.kb.rag.KbRagRankedChunk;
import com.agentlego.backend.kb.vector.KbVectorChunkRow;
import com.agentlego.backend.kb.vector.KbVectorStore;
import com.agentlego.backend.kb.vector.KbVectorStoreConfigMaps;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识库分片向量在 Milvus 中的建表、写入、删除与检索。
 */
@Component
public class MilvusKnowledgeStore implements KbVectorStore {

    static final String F_CHUNK_ID = "chunk_id";
    static final String F_DOCUMENT_ID = "document_id";
    static final String F_CHUNK_INDEX = "chunk_index";
    static final String F_CHUNK_TEXT = "chunk_text";
    static final String F_EMBEDDING = "embedding";

    private static final int VARCHAR_CHUNK_ID = 64;
    private static final int VARCHAR_DOCUMENT_ID = 64;
    private static final int VARCHAR_CHUNK_TEXT = 65535;

    private final MilvusClientCache clientCache;

    public MilvusKnowledgeStore(MilvusClientCache clientCache) {
        this.clientCache = clientCache;
    }

    /**
     * Milvus 不同 metric 下 score 语义不同，统一到「越大越相似」再与控制台阈值比较。
     */
    private static double normalizeScore(io.milvus.param.MetricType metric, float raw) {
        if (metric == io.milvus.param.MetricType.L2) {
            double d = raw;
            return 1.0d / (1.0d + d);
        }
        return raw;
    }

    private static String strField(SearchResultsWrapper.IDScore row, String name) {
        try {
            Object v = row.get(name);
            return v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            return "";
        }
    }

    private static String searchExtra(KbMilvusSettings cfg) {
        return switch (cfg.indexType()) {
            case IVF_FLAT, IVF_SQ8, IVF_PQ -> "{\"nprobe\":16}";
            case HNSW -> "{\"ef\":64}";
            case AUTOINDEX, FLAT, GPU_IVF_FLAT, GPU_IVF_PQ, GPU_BRUTE_FORCE, GPU_CAGRA, DISKANN, SCANN ->
                    "{\"nprobe\":16}";
            default -> "{}";
        };
    }

    private static void ensureCollectionLoaded(MilvusServiceClient client, KbMilvusSettings cfg) {
        LoadCollectionParam load = LoadCollectionParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .withCollectionName(cfg.collectionName())
                .build();
        assertOk(client.loadCollection(load), "Milvus loadCollection");
    }

    private static List<Float> toFloatList(float[] v, int dim) {
        if (v == null || v.length != dim) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "向量维度与集合 embedding_dims 不一致（期望 " + dim + "）",
                    HttpStatus.BAD_REQUEST
            );
        }
        List<Float> l = new ArrayList<>(dim);
        for (float f : v) {
            l.add(f);
        }
        return l;
    }

    private static String truncateText(String t, int maxChars) {
        if (t == null) {
            return "";
        }
        return t.length() <= maxChars ? t : t.substring(0, maxChars);
    }

    private static String escapeExprLiteral(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static KbMilvusSettings resolveMilvus(KbCollectionAggregate col) {
        if (col == null) {
            throw new ApiException("VALIDATION_ERROR", "集合为空", HttpStatus.BAD_REQUEST);
        }
        KbVectorStoreKind kind = KbVectorStoreKind.fromApi(col.getVectorStoreKind());
        if (kind != KbVectorStoreKind.MILVUS) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "暂不支持的 vectorStoreKind: " + kind,
                    HttpStatus.BAD_REQUEST
            );
        }
        Map<String, Object> m = KbVectorStoreConfigMaps.requireNonEmptyFromAggregate(col, "Milvus");
        return KbMilvusSettings.fromConfigMap(m);
    }

    private static void assertOk(R<?> r, String op) {
        if (r.getStatus() == R.Status.Success.getCode()) {
            return;
        }
        throw milvusFail(op, r);
    }

    private static ApiException milvusFail(String op, R<?> r) {
        String msg = r.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = r.getStatus() == null ? "unknown" : r.getStatus().toString();
        }
        if (r.getException() != null) {
            msg = msg + ": " + r.getException().getMessage();
        }
        return new ApiException(
                "UPSTREAM_ERROR",
                op + " 失败：" + msg,
                HttpStatus.BAD_GATEWAY
        );
    }

    @Override
    public void upsertChunks(KbCollectionAggregate col, List<KbVectorChunkRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        KbMilvusSettings cfg = resolveMilvus(col);
        MilvusServiceClient client = clientCache.client(cfg);
        ensureCollection(client, col, cfg);
        List<String> chunkIds = new ArrayList<>();
        List<String> docIds = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();
        int dim = col.getEmbeddingDims();
        for (KbVectorChunkRow r : rows) {
            chunkIds.add(r.chunkId());
            docIds.add(r.documentId());
            indexes.add(r.chunkIndex());
            texts.add(truncateText(r.text(), VARCHAR_CHUNK_TEXT));
            vectors.add(toFloatList(r.vector(), dim));
        }
        List<InsertParam.Field> fields = List.of(
                new InsertParam.Field(F_CHUNK_ID, chunkIds),
                new InsertParam.Field(F_DOCUMENT_ID, docIds),
                new InsertParam.Field(F_CHUNK_INDEX, indexes),
                new InsertParam.Field(F_CHUNK_TEXT, texts),
                new InsertParam.Field(F_EMBEDDING, vectors)
        );
        InsertParam insert = InsertParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .withCollectionName(cfg.collectionName())
                .withFields(fields)
                .build();
        assertOk(client.insert(insert), "Milvus insert");
        FlushParam flush = FlushParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .addCollectionName(cfg.collectionName())
                .withSyncFlush(Boolean.TRUE)
                .build();
        assertOk(client.flush(flush), "Milvus flush");
    }

    @Override
    public void deleteByDocumentId(KbCollectionAggregate col, String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        KbMilvusSettings cfg = resolveMilvus(col);
        MilvusServiceClient client = clientCache.client(cfg);
        String expr = F_DOCUMENT_ID + " == \"" + escapeExprLiteral(documentId.trim()) + "\"";
        DeleteParam del = DeleteParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .withCollectionName(cfg.collectionName())
                .withExpr(expr)
                .build();
        R<?> r = client.delete(del);
        if (r.getStatus() != R.Status.Success.getCode()) {
            if (Objects.equals(r.getStatus(), R.Status.CollectionNotExists.getCode())) {
                return;
            }
            throw milvusFail("Milvus delete", r);
        }
        FlushParam flush = FlushParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .addCollectionName(cfg.collectionName())
                .withSyncFlush(Boolean.TRUE)
                .build();
        assertOk(client.flush(flush), "Milvus flush(delete)");
    }

    @Override
    public void dropPhysicalCollection(KbCollectionAggregate col) {
        KbMilvusSettings cfg = resolveMilvus(col);
        MilvusServiceClient client = clientCache.client(cfg);
        DropCollectionParam drop = DropCollectionParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .withCollectionName(cfg.collectionName())
                .build();
        R<?> r = client.dropCollection(drop);
        if (r.getStatus() != R.Status.Success.getCode()) {
            if (Objects.equals(r.getStatus(), R.Status.CollectionNotExists.getCode())) {
                return;
            }
            throw milvusFail("Milvus dropCollection", r);
        }
    }

    @Override
    public List<KbRagRankedChunk> search(
            KbCollectionAggregate col,
            float[] queryVector,
            int topK,
            double minScore
    ) {
        if (topK <= 0) {
            return List.of();
        }
        KbMilvusSettings cfg = resolveMilvus(col);
        MilvusServiceClient client = clientCache.client(cfg);
        // 与 upsert 一致：检索前须保证物理 collection 已创建，否则尚未入库时 loadCollection 会报 collection not found
        ensureCollection(client, col, cfg);
        int dim = col.getEmbeddingDims();
        List<List<Float>> q = List.of(toFloatList(queryVector, dim));
        SearchParam sp = SearchParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .withCollectionName(cfg.collectionName())
                .withVectorFieldName(F_EMBEDDING)
                .withMetricType(cfg.metricType())
                .withTopK(topK)
                .withFloatVectors(q)
                .withOutFields(List.of(F_CHUNK_ID, F_DOCUMENT_ID, F_CHUNK_TEXT))
                .withParams(searchExtra(cfg))
                .build();
        R<io.milvus.grpc.SearchResults> resp = client.search(sp);
        assertOk(resp, "Milvus search");
        if (resp.getData() == null || !resp.getData().hasResults()) {
            return List.of();
        }
        SearchResultsWrapper w = new SearchResultsWrapper(resp.getData().getResults());
        List<KbRagRankedChunk> out = new ArrayList<>();
        List<SearchResultsWrapper.IDScore> scores;
        try {
            scores = w.getIDScore(0);
        } catch (Exception e) {
            throw new ApiException("UPSTREAM_ERROR", "Milvus 结果解析失败：" + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        if (scores == null) {
            return List.of();
        }
        for (SearchResultsWrapper.IDScore idScore : scores) {
            double sc = normalizeScore(cfg.metricType(), idScore.getScore());
            if (sc < minScore) {
                continue;
            }
            String chunkId = idScore.getStrID();
            String docId = strField(idScore, F_DOCUMENT_ID);
            String text = strField(idScore, F_CHUNK_TEXT);
            out.add(new KbRagRankedChunk(chunkId, docId, text == null ? "" : text, sc));
        }
        return out;
    }

    private void ensureCollection(MilvusServiceClient client, KbCollectionAggregate col, KbMilvusSettings cfg) {
        R<Boolean> hasR = client.hasCollection(HasCollectionParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .withCollectionName(cfg.collectionName())
                .build());
        assertOk(hasR, "Milvus hasCollection");
        Boolean exists = hasR.getData();
        if (Boolean.TRUE.equals(exists)) {
            ensureCollectionLoaded(client, cfg);
            return;
        }
        int dim = col.getEmbeddingDims();
        List<FieldType> fields = new ArrayList<>();
        fields.add(FieldType.newBuilder()
                .withName(F_CHUNK_ID)
                .withDataType(DataType.VarChar)
                .withMaxLength(VARCHAR_CHUNK_ID)
                .withPrimaryKey(true)
                .build());
        fields.add(FieldType.newBuilder()
                .withName(F_DOCUMENT_ID)
                .withDataType(DataType.VarChar)
                .withMaxLength(VARCHAR_DOCUMENT_ID)
                .build());
        fields.add(FieldType.newBuilder()
                .withName(F_CHUNK_INDEX)
                .withDataType(DataType.Int32)
                .build());
        fields.add(FieldType.newBuilder()
                .withName(F_CHUNK_TEXT)
                .withDataType(DataType.VarChar)
                .withMaxLength(VARCHAR_CHUNK_TEXT)
                .build());
        fields.add(FieldType.newBuilder()
                .withName(F_EMBEDDING)
                .withDataType(DataType.FloatVector)
                .withDimension(dim)
                .build());
        CreateCollectionParam create = CreateCollectionParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .withCollectionName(cfg.collectionName())
                .withFieldTypes(fields)
                .build();
        assertOk(client.createCollection(create), "Milvus createCollection");
        CreateIndexParam index = CreateIndexParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .withCollectionName(cfg.collectionName())
                .withFieldName(F_EMBEDDING)
                .withIndexType(cfg.indexType())
                .withMetricType(cfg.metricType())
                .withSyncMode(Boolean.TRUE)
                .withExtraParam(cfg.indexExtraParams() == null || cfg.indexExtraParams().isBlank()
                        ? "{}"
                        : cfg.indexExtraParams())
                .build();
        assertOk(client.createIndex(index), "Milvus createIndex");
        ensureCollectionLoaded(client, cfg);
    }
}
