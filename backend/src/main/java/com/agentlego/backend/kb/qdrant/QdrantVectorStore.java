package com.agentlego.backend.kb.qdrant;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbVectorStoreKind;
import com.agentlego.backend.kb.rag.KbRagRankedChunk;
import com.agentlego.backend.kb.vector.KbVectorChunkRow;
import com.agentlego.backend.kb.vector.KbVectorStore;
import com.agentlego.backend.kb.vector.KbVectorStoreConfigMaps;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识库分片向量在 Qdrant（HTTP REST）中的建集合、写入、删除与检索。
 */
@Component
public class QdrantVectorStore implements KbVectorStore {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_CHUNK_TEXT_CHARS = 65535;

    private static final String P_CHUNK_ID = "chunk_id";
    private static final String P_DOCUMENT_ID = "document_id";
    private static final String P_CHUNK_INDEX = "chunk_index";
    private static final String P_CHUNK_TEXT = "chunk_text";

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;

    public QdrantVectorStore(
            @Qualifier("kbVectorStoreHttpClient") OkHttpClient http,
            ObjectMapper objectMapper
    ) {
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    private static double normalizeScore(KbQdrantSettings.QdrantDistance d, double raw) {
        if (d == KbQdrantSettings.QdrantDistance.EUCLID) {
            return 1.0d / (1.0d + raw);
        }
        return raw;
    }

    private static String textOrEmpty(JsonNode n) {
        if (n == null || n.isNull()) {
            return "";
        }
        if (n.isTextual()) {
            return n.asText();
        }
        return n.asText("");
    }

    private static Request.Builder applyApiKey(Request.Builder b, KbQdrantSettings cfg) {
        if (cfg.apiKey() != null && !cfg.apiKey().isBlank()) {
            b.header("api-key", cfg.apiKey());
        }
        return b;
    }

    private static long parsePointId(String chunkId) {
        if (chunkId == null || chunkId.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "chunkId 不能为空", HttpStatus.BAD_REQUEST);
        }
        try {
            return Long.parseLong(chunkId.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "Qdrant 点 ID 须为整数字符串（与 Snowflake chunk_id 一致）",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private static float[] toFloatArray(float[] v, int dim) {
        if (v == null || v.length != dim) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "向量维度与集合 embedding_dims 不一致（期望 " + dim + "）",
                    HttpStatus.BAD_REQUEST
            );
        }
        return v;
    }

    private static String truncate(String t, int max) {
        if (t == null) {
            return "";
        }
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String urlEnc(String collectionName) {
        return collectionName.replace(" ", "%20");
    }

    private static KbQdrantSettings resolveQdrant(KbCollectionAggregate col) {
        if (col == null) {
            throw new ApiException("VALIDATION_ERROR", "集合为空", HttpStatus.BAD_REQUEST);
        }
        KbVectorStoreKind kind = KbVectorStoreKind.fromApi(col.getVectorStoreKind());
        if (kind != KbVectorStoreKind.QDRANT) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "非 QDRANT 集合不应路由到 QdrantVectorStore: " + kind,
                    HttpStatus.BAD_REQUEST
            );
        }
        Map<String, Object> m = KbVectorStoreConfigMaps.requireNonEmptyFromAggregate(col, "Qdrant");
        return KbQdrantSettings.fromConfigMap(m);
    }

    private static ApiException upstream(String op, int code, String body) {
        String msg = body == null || body.isBlank() ? ("HTTP " + code) : body;
        return new ApiException("UPSTREAM_ERROR", op + " 失败：" + msg, HttpStatus.BAD_GATEWAY);
    }

    private static ApiException upstreamIo(String op, IOException e) {
        return new ApiException(
                "UPSTREAM_ERROR",
                op + " 失败：" + e.getMessage(),
                HttpStatus.BAD_GATEWAY
        );
    }

    @Override
    public void upsertChunks(KbCollectionAggregate col, List<KbVectorChunkRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        KbQdrantSettings cfg = resolveQdrant(col);
        int dim = col.getEmbeddingDims();
        ensureCollection(cfg, dim);
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode points = root.putArray("points");
        for (KbVectorChunkRow r : rows) {
            ObjectNode p = points.addObject();
            p.put("id", parsePointId(r.chunkId()));
            ArrayNode vec = p.putArray("vector");
            for (float f : toFloatArray(r.vector(), dim)) {
                vec.add(f);
            }
            ObjectNode payload = p.putObject("payload");
            payload.put(P_CHUNK_ID, r.chunkId());
            payload.put(P_DOCUMENT_ID, r.documentId());
            payload.put(P_CHUNK_INDEX, r.chunkIndex());
            payload.put(P_CHUNK_TEXT, truncate(r.text(), MAX_CHUNK_TEXT_CHARS));
        }
        // Qdrant 官方 Upsert 为 PUT /collections/{name}/points（见 api.qdrant.tech）。
        // 若误用 POST，服务端会按其它 PointInsertOperations 变体解析，易报 missing field `ids`。
        putJson(cfg, "/collections/" + urlEnc(cfg.collectionName()) + "/points?wait=true", root);
    }

    @Override
    public void deleteByDocumentId(KbCollectionAggregate col, String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        KbQdrantSettings cfg = resolveQdrant(col);
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode filter = root.putObject("filter");
        ArrayNode must = filter.putArray("must");
        ObjectNode cond = must.addObject();
        cond.put("key", P_DOCUMENT_ID);
        ObjectNode match = cond.putObject("match");
        match.put("value", documentId.trim());
        execPost(cfg, "/collections/" + urlEnc(cfg.collectionName()) + "/points/delete?wait=true", root, true);
    }

    @Override
    public void dropPhysicalCollection(KbCollectionAggregate col) {
        KbQdrantSettings cfg = resolveQdrant(col);
        Request.Builder b = new Request.Builder()
                .url(cfg.baseUrl() + "/collections/" + urlEnc(cfg.collectionName()))
                .delete();
        applyApiKey(b, cfg);
        Request req = b.build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() == null ? "" : resp.body().string();
            if (resp.isSuccessful() || resp.code() == 404) {
                return;
            }
            throw upstream("Qdrant delete collection", resp.code(), body);
        } catch (IOException e) {
            throw upstreamIo("Qdrant delete collection", e);
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
        KbQdrantSettings cfg = resolveQdrant(col);
        int dim = col.getEmbeddingDims();
        // 与 upsertChunks 一致：若物理 collection 尚未创建（从未入库、或 Qdrant 侧被删），先按维度建表，避免 /points/search 直接 404。
        ensureCollection(cfg, dim);
        float[] qv = toFloatArray(queryVector, dim);
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode vec = root.putArray("vector");
        for (float f : qv) {
            vec.add(f);
        }
        root.put("limit", topK);
        root.put("with_payload", true);
        if (cfg.distance() != KbQdrantSettings.QdrantDistance.EUCLID) {
            root.put("score_threshold", minScore);
        }
        String json = execPostReturningBody(
                cfg,
                "/collections/" + urlEnc(cfg.collectionName()) + "/points/search",
                root,
                false
        );
        List<KbRagRankedChunk> out = new ArrayList<>();
        try {
            JsonNode tree = objectMapper.readTree(json);
            JsonNode result = tree.get("result");
            if (result == null || !result.isArray()) {
                return out;
            }
            for (JsonNode hit : result) {
                double raw = hit.path("score").asDouble();
                double sc = normalizeScore(cfg.distance(), raw);
                if (sc < minScore) {
                    continue;
                }
                JsonNode payload = hit.get("payload");
                String chunkId = payload == null ? "" : textOrEmpty(payload.get(P_CHUNK_ID));
                String docId = payload == null ? "" : textOrEmpty(payload.get(P_DOCUMENT_ID));
                String text = payload == null ? "" : textOrEmpty(payload.get(P_CHUNK_TEXT));
                out.add(new KbRagRankedChunk(chunkId, docId, text, sc));
            }
        } catch (Exception e) {
            throw new ApiException(
                    "UPSTREAM_ERROR",
                    "Qdrant 结果解析失败：" + e.getMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
        return out;
    }

    private void ensureCollection(KbQdrantSettings cfg, int dim) {
        Request.Builder getB = new Request.Builder()
                .url(cfg.baseUrl() + "/collections/" + urlEnc(cfg.collectionName()));
        applyApiKey(getB, cfg);
        try (Response resp = http.newCall(getB.get().build()).execute()) {
            if (resp.code() == 200) {
                return;
            }
            if (resp.code() != 404) {
                String body = resp.body() == null ? "" : resp.body().string();
                throw upstream("Qdrant get collection", resp.code(), body);
            }
        } catch (IOException e) {
            throw upstreamIo("Qdrant get collection", e);
        }
        ObjectNode create = objectMapper.createObjectNode();
        ObjectNode vectors = create.putObject("vectors");
        vectors.put("size", dim);
        vectors.put("distance", cfg.qdrantDistanceName());
        putJson(cfg, "/collections/" + urlEnc(cfg.collectionName()), create);
    }

    private void putJson(KbQdrantSettings cfg, String pathAndQuery, ObjectNode body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new ApiException("VALIDATION_ERROR", "Qdrant 请求体序列化失败", HttpStatus.BAD_REQUEST);
        }
        Request.Builder b = new Request.Builder()
                .url(cfg.baseUrl() + pathAndQuery)
                .put(RequestBody.create(json.getBytes(StandardCharsets.UTF_8), JSON));
        applyApiKey(b, cfg);
        try (Response resp = http.newCall(b.build()).execute()) {
            String respBody = resp.body() == null ? "" : resp.body().string();
            if (resp.isSuccessful()) {
                return;
            }
            throw upstream("Qdrant put collection", resp.code(), respBody);
        } catch (IOException e) {
            throw upstreamIo("Qdrant put collection", e);
        }
    }

    private void postJson(KbQdrantSettings cfg, String pathAndQuery, ObjectNode body) {
        execPost(cfg, pathAndQuery, body, false);
    }

    private void execPost(KbQdrantSettings cfg, String pathAndQuery, ObjectNode body, boolean allow404Empty) {
        Request req = buildPost(cfg, pathAndQuery, body);
        try (Response resp = http.newCall(req).execute()) {
            String respBody = resp.body() == null ? "" : resp.body().string();
            if (resp.isSuccessful()) {
                return;
            }
            if (allow404Empty && resp.code() == 404) {
                return;
            }
            throw upstream("Qdrant " + pathAndQuery, resp.code(), respBody);
        } catch (IOException e) {
            throw upstreamIo("Qdrant " + pathAndQuery, e);
        }
    }

    private String execPostReturningBody(KbQdrantSettings cfg, String pathAndQuery, ObjectNode body, boolean allow404) {
        Request req = buildPost(cfg, pathAndQuery, body);
        try (Response resp = http.newCall(req).execute()) {
            String respBody = resp.body() == null ? "" : resp.body().string();
            if (resp.isSuccessful()) {
                return respBody;
            }
            if (allow404 && resp.code() == 404) {
                return "{\"result\":[]}";
            }
            throw upstream("Qdrant " + pathAndQuery, resp.code(), respBody);
        } catch (IOException e) {
            throw upstreamIo("Qdrant " + pathAndQuery, e);
        }
    }

    private Request buildPost(KbQdrantSettings cfg, String pathAndQuery, ObjectNode body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new ApiException("VALIDATION_ERROR", "Qdrant 请求体序列化失败", HttpStatus.BAD_REQUEST);
        }
        Request.Builder b = new Request.Builder()
                .url(cfg.baseUrl() + pathAndQuery)
                .post(RequestBody.create(json.getBytes(StandardCharsets.UTF_8), JSON));
        applyApiKey(b, cfg);
        return b.build();
    }
}
