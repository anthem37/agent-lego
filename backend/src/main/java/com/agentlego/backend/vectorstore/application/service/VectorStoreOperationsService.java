package com.agentlego.backend.vectorstore.application.service;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.kb.domain.KbVectorStoreKind;
import com.agentlego.backend.kb.infrastructure.persistence.KbCollectionDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbCollectionMapper;
import com.agentlego.backend.kb.milvus.KbMilvusSettings;
import com.agentlego.backend.kb.milvus.MilvusClientCache;
import com.agentlego.backend.kb.qdrant.KbQdrantSettings;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import com.agentlego.backend.vectorstore.application.dto.*;
import com.agentlego.backend.vectorstore.domain.VectorStoreCollectionBindingRepository;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileAggregate;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * 向量库 profile 维度的常用运维：探测、列集合、统计、嵌入自检、物理删 collection 等。
 */
@Service
public class VectorStoreOperationsService {

    private final VectorStoreProfileRepository profileRepository;
    private final KbCollectionMapper kbCollectionMapper;
    private final VectorStoreCollectionBindingRepository collectionBindingRepository;
    private final MilvusClientCache milvusClientCache;
    private final OkHttpClient http;
    private final ObjectMapper objectMapper;
    private final ModelEmbeddingClient embeddingClient;

    public VectorStoreOperationsService(
            VectorStoreProfileRepository profileRepository,
            KbCollectionMapper kbCollectionMapper,
            VectorStoreCollectionBindingRepository collectionBindingRepository,
            MilvusClientCache milvusClientCache,
            @Qualifier("kbVectorStoreHttpClient") OkHttpClient http,
            ObjectMapper objectMapper,
            ModelEmbeddingClient embeddingClient
    ) {
        this.profileRepository = profileRepository;
        this.kbCollectionMapper = kbCollectionMapper;
        this.collectionBindingRepository = collectionBindingRepository;
        this.milvusClientCache = milvusClientCache;
        this.http = http;
        this.objectMapper = objectMapper;
        this.embeddingClient = embeddingClient;
    }

    private static KbMilvusSettings milvusSettings(VectorStoreProfileAggregate p) {
        Map<String, Object> m = JsonMaps.parseObject(p.getVectorStoreConfigJson());
        return KbMilvusSettings.fromConfigMap(m, false);
    }

    private static KbQdrantSettings qdrantSettings(VectorStoreProfileAggregate p) {
        Map<String, Object> m = JsonMaps.parseObject(p.getVectorStoreConfigJson());
        return KbQdrantSettings.fromConfigMap(m, false);
    }

    private static void milvusAssertOk(R<?> r, String op) {
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
        return new ApiException("UPSTREAM_ERROR", op + " 失败：" + msg, HttpStatus.BAD_GATEWAY);
    }

    private static String summarizeHealth(CheckHealthResponse h) {
        if (h == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("healthy=").append(h.getIsHealthy());
        if (h.getReasonsCount() > 0) {
            sb.append("; ");
            for (int i = 0; i < h.getReasonsCount(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(h.getReasons(i));
            }
        }
        return sb.toString();
    }

    private static void applyQdrantApiKey(Request.Builder b, KbQdrantSettings cfg) {
        if (cfg.apiKey() != null && !cfg.apiKey().isBlank()) {
            b.header("api-key", cfg.apiKey());
        }
    }

    private static String urlEnc(String collectionName) {
        return collectionName.replace(" ", "%20");
    }

    private static String findMilvusPrimaryKeyName(CollectionSchema schema) {
        for (FieldSchema f : schema.getFieldsList()) {
            if (f.getIsPrimaryKey()) {
                return f.getName();
            }
        }
        return null;
    }

    private static String buildMilvusMatchAllExpr(CollectionSchema schema) {
        FieldSchema pk = null;
        for (FieldSchema f : schema.getFieldsList()) {
            if (f.getIsPrimaryKey()) {
                pk = f;
                break;
            }
        }
        if (pk == null) {
            return "true";
        }
        String name = pk.getName();
        DataType dt = pk.getDataType();
        return switch (dt) {
            case Int64, Int32, Int16, Int8 -> name + " >= 0";
            case VarChar, String -> name + " != \"\"";
            case Bool -> "(" + name + " == true || " + name + " == false)";
            case Float, Double -> name + " >= 0";
            default -> name + " >= 0";
        };
    }

    private static List<String> buildMilvusScalarOutFields(CollectionSchema schema) {
        List<String> out = new ArrayList<>();
        for (FieldSchema f : schema.getFieldsList()) {
            DataType dt = f.getDataType();
            if (dt == DataType.FloatVector
                    || dt == DataType.BinaryVector
                    || dt == DataType.Float16Vector
                    || dt == DataType.BFloat16Vector
                    || dt == DataType.SparseFloatVector) {
                continue;
            }
            out.add(f.getName());
        }
        return out;
    }

    private static Map<String, Object> truncatePayloadForPreview(Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String s) {
                out.put(e.getKey(), s.length() > 2000 ? s.substring(0, 2000) + "…(已截断)" : s);
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    public VectorStoreUsageDto usage(String profileId) {
        requireProfile(profileId);
        int n = kbCollectionMapper.countByVectorStoreProfileId(profileId);
        List<KbCollectionDO> rows = kbCollectionMapper.findByVectorStoreProfileId(profileId);
        List<VectorStoreUsageDto.KbCollectionRefDto> refs = new ArrayList<>();
        if (rows != null) {
            for (KbCollectionDO row : rows) {
                VectorStoreUsageDto.KbCollectionRefDto r = new VectorStoreUsageDto.KbCollectionRefDto();
                r.setId(row.getId());
                r.setName(row.getName());
                Map<String, Object> cfg = JsonMaps.parseObject(row.getVectorStoreConfigJson());
                r.setPhysicalCollectionName(JsonMaps.getString(cfg, "collectionName", ""));
                refs.add(r);
            }
        }
        VectorStoreUsageDto out = new VectorStoreUsageDto();
        out.setKbCollectionCount(n);
        out.setKbCollections(refs);
        return out;
    }

    public VectorStoreProbeResultDto probe(String profileId) {
        long t0 = System.nanoTime();
        VectorStoreProfileAggregate p = requireProfile(profileId);
        KbVectorStoreKind kind = KbVectorStoreKind.fromApi(p.getVectorStoreKind());
        VectorStoreProbeResultDto dto = new VectorStoreProbeResultDto();
        try {
            if (kind == KbVectorStoreKind.MILVUS) {
                KbMilvusSettings cfg = milvusSettings(p);
                MilvusServiceClient client = milvusClientCache.client(cfg);
                R<GetVersionResponse> ver = client.getVersion();
                milvusAssertOk(ver, "getVersion");
                String version = ver.getData() == null ? "" : ver.getData().getVersion();
                R<CheckHealthResponse> health = client.checkHealth();
                milvusAssertOk(health, "checkHealth");
                String healthStr = summarizeHealth(health.getData());
                ShowCollectionsParam scp = ShowCollectionsParam.newBuilder()
                        .withDatabaseName(cfg.databaseName())
                        .build();
                R<ShowCollectionsResponse> sc = client.showCollections(scp);
                milvusAssertOk(sc, "showCollections");
                int cnt = sc.getData() == null ? 0 : sc.getData().getCollectionNamesCount();
                dto.setOk(true);
                dto.setServerVersion(version);
                dto.setHealthSummary(healthStr);
                dto.setCollectionCount(cnt);
                dto.setMessage("Milvus 连接正常");
            } else {
                KbQdrantSettings cfg = qdrantSettings(p);
                String body = qdrantGet(cfg, "/collections");
                JsonNode tree = objectMapper.readTree(body);
                JsonNode result = tree.get("result");
                int cnt = 0;
                if (result != null && result.has("collections") && result.get("collections").isArray()) {
                    cnt = result.get("collections").size();
                }
                dto.setOk(true);
                dto.setServerVersion("Qdrant REST");
                dto.setHealthSummary("HTTP 200");
                dto.setCollectionCount(cnt);
                dto.setMessage("Qdrant 连接正常");
            }
        } catch (ApiException e) {
            dto.setOk(false);
            dto.setMessage(e.getMessage());
        } catch (Exception e) {
            dto.setOk(false);
            dto.setMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        dto.setLatencyMs((System.nanoTime() - t0) / 1_000_000L);
        return dto;
    }

    public List<VectorStoreCollectionSummaryDto> listCollections(String profileId) {
        VectorStoreProfileAggregate p = requireProfile(profileId);
        KbVectorStoreKind kind = KbVectorStoreKind.fromApi(p.getVectorStoreKind());
        if (kind == KbVectorStoreKind.MILVUS) {
            KbMilvusSettings cfg = milvusSettings(p);
            MilvusServiceClient client = milvusClientCache.client(cfg);
            ShowCollectionsParam scp = ShowCollectionsParam.newBuilder()
                    .withDatabaseName(cfg.databaseName())
                    .build();
            R<ShowCollectionsResponse> sc = client.showCollections(scp);
            milvusAssertOk(sc, "showCollections");
            ShowCollectionsResponse data = sc.getData();
            if (data == null) {
                return List.of();
            }
            List<VectorStoreCollectionSummaryDto> out = new ArrayList<>();
            int n = data.getCollectionNamesCount();
            for (int i = 0; i < n; i++) {
                VectorStoreCollectionSummaryDto row = new VectorStoreCollectionSummaryDto();
                row.setName(data.getCollectionNames(i));
                if (i < data.getInMemoryPercentagesCount()) {
                    row.setLoadedPercent((int) data.getInMemoryPercentages(i));
                }
                if (i < data.getQueryServiceAvailableCount()) {
                    row.setQueryServiceAvailable(data.getQueryServiceAvailable(i));
                }
                out.add(row);
            }
            return out;
        }
        KbQdrantSettings cfg = qdrantSettings(p);
        try {
            String body = qdrantGet(cfg, "/collections");
            JsonNode tree = objectMapper.readTree(body);
            JsonNode collections = tree.path("result").path("collections");
            List<VectorStoreCollectionSummaryDto> out = new ArrayList<>();
            if (collections.isArray()) {
                for (JsonNode c : collections) {
                    VectorStoreCollectionSummaryDto row = new VectorStoreCollectionSummaryDto();
                    row.setName(c.path("name").asText(""));
                    out.add(row);
                }
            }
            return out;
        } catch (IOException e) {
            throw new ApiException("UPSTREAM_ERROR", "Qdrant 列集合失败：" + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    public VectorStoreCollectionStatsDto collectionStats(String profileId, String collectionName) {
        String cn = Objects.requireNonNull(collectionName, "collectionName").trim();
        if (cn.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "collectionName 不能为空", HttpStatus.BAD_REQUEST);
        }
        VectorStoreProfileAggregate p = requireProfile(profileId);
        KbVectorStoreKind kind = KbVectorStoreKind.fromApi(p.getVectorStoreKind());
        if (kind == KbVectorStoreKind.MILVUS) {
            KbMilvusSettings cfg = milvusSettings(p);
            MilvusServiceClient client = milvusClientCache.client(cfg);
            GetCollectionStatisticsParam param = GetCollectionStatisticsParam.newBuilder()
                    .withDatabaseName(cfg.databaseName())
                    .withCollectionName(cn)
                    .build();
            R<GetCollectionStatisticsResponse> r = client.getCollectionStatistics(param);
            milvusAssertOk(r, "getCollectionStatistics");
            GetCollectionStatisticsResponse data = r.getData();
            VectorStoreCollectionStatsDto dto = new VectorStoreCollectionStatsDto();
            dto.setCollectionName(cn);
            Map<String, String> raw = new LinkedHashMap<>();
            Long rowCount = null;
            if (data != null) {
                for (int i = 0; i < data.getStatsCount(); i++) {
                    KeyValuePair kv = data.getStats(i);
                    if (kv != null) {
                        raw.put(kv.getKey(), kv.getValue());
                        if ("row_count".equalsIgnoreCase(kv.getKey())) {
                            try {
                                rowCount = Long.parseLong(kv.getValue().trim());
                            } catch (NumberFormatException ignored) {
                                // ignore
                            }
                        }
                    }
                }
            }
            dto.setRowCount(rowCount);
            dto.setRawStats(raw);
            return dto;
        }
        KbQdrantSettings cfg = qdrantSettings(p);
        try {
            String body = qdrantGet(cfg, "/collections/" + urlEnc(cn));
            JsonNode tree = objectMapper.readTree(body);
            JsonNode result = tree.get("result");
            VectorStoreCollectionStatsDto dto = new VectorStoreCollectionStatsDto();
            dto.setCollectionName(cn);
            Map<String, String> raw = new LinkedHashMap<>();
            Long points = null;
            if (result != null) {
                if (result.has("points_count")) {
                    points = result.get("points_count").asLong();
                    raw.put("points_count", String.valueOf(points));
                }
                if (result.has("status")) {
                    raw.put("status", result.get("status").toString());
                }
            }
            dto.setRowCount(points);
            dto.setRawStats(raw);
            return dto;
        } catch (IOException e) {
            throw new ApiException("UPSTREAM_ERROR", "Qdrant 获取集合信息失败：" + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    public void loadCollection(String profileId, String collectionName) {
        String cn = Objects.requireNonNull(collectionName, "collectionName").trim();
        if (cn.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "collectionName 不能为空", HttpStatus.BAD_REQUEST);
        }
        VectorStoreProfileAggregate p = requireProfile(profileId);
        KbVectorStoreKind kind = KbVectorStoreKind.fromApi(p.getVectorStoreKind());
        if (kind != KbVectorStoreKind.MILVUS) {
            throw new ApiException("VALIDATION_ERROR", "仅 Milvus 支持 loadCollection", HttpStatus.BAD_REQUEST);
        }
        KbMilvusSettings cfg = milvusSettings(p);
        MilvusServiceClient client = milvusClientCache.client(cfg);
        LoadCollectionParam load = LoadCollectionParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .withCollectionName(cn)
                .build();
        R<?> r = client.loadCollection(load);
        milvusAssertOk(r, "loadCollection");
    }

    public void dropPhysicalCollection(String profileId, DropVectorStoreCollectionRequest req) {
        if (!req.getCollectionName().trim().equals(req.getConfirmCollectionName().trim())) {
            throw new ApiException("VALIDATION_ERROR", "两次输入的 collection 名称不一致，已取消删除", HttpStatus.BAD_REQUEST);
        }
        String cn = req.getCollectionName().trim();
        if (cn.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "collectionName 不能为空", HttpStatus.BAD_REQUEST);
        }
        VectorStoreProfileAggregate p = requireProfile(profileId);
        KbVectorStoreKind kind = KbVectorStoreKind.fromApi(p.getVectorStoreKind());
        if (kind == KbVectorStoreKind.MILVUS) {
            KbMilvusSettings cfg = milvusSettings(p);
            MilvusServiceClient client = milvusClientCache.client(cfg);
            DropCollectionParam drop = DropCollectionParam.newBuilder()
                    .withDatabaseName(cfg.databaseName())
                    .withCollectionName(cn)
                    .build();
            R<?> r = client.dropCollection(drop);
            if (r.getStatus() != R.Status.Success.getCode()) {
                if (Objects.equals(r.getStatus(), R.Status.CollectionNotExists.getCode())) {
                    return;
                }
                throw milvusFail("dropCollection", r);
            }
            return;
        }
        KbQdrantSettings cfg = qdrantSettings(p);
        Request.Builder b = new Request.Builder()
                .url(cfg.baseUrl() + "/collections/" + urlEnc(cn))
                .delete();
        applyQdrantApiKey(b, cfg);
        try (Response resp = http.newCall(b.build()).execute()) {
            if (resp.isSuccessful() || resp.code() == 404) {
                return;
            }
            String body = resp.body() == null ? "" : resp.body().string();
            throw new ApiException("UPSTREAM_ERROR", "Qdrant 删除 collection 失败 HTTP " + resp.code() + ": " + body, HttpStatus.BAD_GATEWAY);
        } catch (IOException e) {
            throw new ApiException("UPSTREAM_ERROR", "Qdrant 删除失败：" + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    public VectorStoreEmbeddingProbeResultDto probeEmbedding(String profileId, VectorStoreEmbeddingProbeRequest req) {
        VectorStoreProfileAggregate p = requireProfile(profileId);
        String modelId = p.getEmbeddingModelId();
        List<float[]> vecs = embeddingClient.embed(modelId, List.of(req.getText().trim()));
        VectorStoreEmbeddingProbeResultDto out = new VectorStoreEmbeddingProbeResultDto();
        out.setEmbeddingModelId(modelId);
        if (vecs.isEmpty() || vecs.get(0) == null) {
            out.setOk(false);
            out.setMessage("嵌入服务返回空向量");
            return out;
        }
        float[] v = vecs.get(0);
        out.setVectorDimension(v.length);
        out.setDimensionMatchesProfile(v.length == p.getEmbeddingDims());
        double sum = 0;
        for (float f : v) {
            sum += (double) f * (double) f;
        }
        out.setVectorNorm(Math.sqrt(sum));
        out.setOk(true);
        out.setMessage(out.isDimensionMatchesProfile() ? "维度与 profile 一致" : "维度与 profile 声明不一致，请检查模型或 profile");
        return out;
    }

    private VectorStoreProfileAggregate requireProfile(String profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "vectorStoreProfile 未找到", HttpStatus.NOT_FOUND));
    }

    private String qdrantGet(KbQdrantSettings cfg, String path) throws IOException {
        Request.Builder b = new Request.Builder().url(cfg.baseUrl() + path).get();
        applyQdrantApiKey(b, cfg);
        try (Response resp = http.newCall(b.build()).execute()) {
            String body = resp.body() == null ? "" : resp.body().string();
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + ": " + body);
            }
            return body;
        }
    }

    private String qdrantPostJson(KbQdrantSettings cfg, String path, String jsonBody) throws IOException {
        RequestBody rb = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));
        Request.Builder b = new Request.Builder().url(cfg.baseUrl() + path).post(rb);
        applyQdrantApiKey(b, cfg);
        try (Response resp = http.newCall(b.build()).execute()) {
            String body = resp.body() == null ? "" : resp.body().string();
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + ": " + body);
            }
            return body;
        }
    }

    /**
     * Qdrant：scroll 抽样预览点 payload；Milvus：describe + query 标量字段（不含向量）。
     */
    public VectorStorePointsPreviewDto pointsPreview(String profileId, String collectionName, int limit, String cursor) {
        String cn = Objects.requireNonNull(collectionName, "collectionName").trim();
        if (cn.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "collectionName 不能为空", HttpStatus.BAD_REQUEST);
        }
        int lim = Math.min(100, Math.max(1, limit));
        VectorStoreProfileAggregate p = requireProfile(profileId);
        KbVectorStoreKind kind = KbVectorStoreKind.fromApi(p.getVectorStoreKind());
        VectorStorePointsPreviewDto out = new VectorStorePointsPreviewDto();
        out.setCollectionName(cn);
        if (kind == KbVectorStoreKind.MILVUS) {
            return pointsPreviewMilvus(p, cn, lim, cursor);
        }
        KbQdrantSettings cfg = qdrantSettings(p);
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("limit", lim);
            body.put("with_payload", true);
            body.put("with_vector", false);
            if (cursor != null && !cursor.isBlank()) {
                body.set("offset", objectMapper.readTree(cursor.trim()));
            }
            String path = "/collections/" + urlEnc(cn) + "/points/scroll";
            String respBody = qdrantPostJson(cfg, path, body.toString());
            JsonNode tree = objectMapper.readTree(respBody);
            JsonNode result = tree.get("result");
            if (result == null) {
                return out;
            }
            JsonNode points = result.get("points");
            if (points != null && points.isArray()) {
                for (JsonNode pt : points) {
                    VectorStorePointPreviewRowDto row = new VectorStorePointPreviewRowDto();
                    if (pt.has("id")) {
                        JsonNode idNode = pt.get("id");
                        if (idNode.isIntegralNumber()) {
                            row.setId(String.valueOf(idNode.asLong()));
                        } else if (idNode.isTextual()) {
                            row.setId(idNode.asText());
                        } else {
                            row.setId(idNode.toString());
                        }
                    }
                    if (pt.has("payload") && pt.get("payload").isObject()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = objectMapper.convertValue(pt.get("payload"), Map.class);
                        row.setPayload(truncatePayloadForPreview(m));
                    }
                    out.getRows().add(row);
                }
            }
            JsonNode next = result.get("next_page_offset");
            if (next != null && !next.isNull()) {
                out.setNextCursor(next.toString());
            }
            return out;
        } catch (IOException e) {
            throw new ApiException("UPSTREAM_ERROR", "Qdrant scroll 失败：" + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private VectorStorePointsPreviewDto pointsPreviewMilvus(VectorStoreProfileAggregate p, String cn, int lim, String cursor) {
        VectorStorePointsPreviewDto out = new VectorStorePointsPreviewDto();
        out.setCollectionName(cn);
        KbMilvusSettings cfg = milvusSettings(p);
        MilvusServiceClient client = milvusClientCache.client(cfg);
        LoadCollectionParam load = LoadCollectionParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .withCollectionName(cn)
                .build();
        R<?> loadR = client.loadCollection(load);
        milvusAssertOk(loadR, "loadCollection");

        R<DescribeCollectionResponse> descR = client.describeCollection(
                DescribeCollectionParam.newBuilder()
                        .withDatabaseName(cfg.databaseName())
                        .withCollectionName(cn)
                        .build());
        milvusAssertOk(descR, "describeCollection");
        DescribeCollectionResponse desc = descR.getData();
        if (desc == null || !desc.hasSchema()) {
            out.setHint("无法读取 collection schema");
            return out;
        }
        CollectionSchema schema = desc.getSchema();
        String expr = buildMilvusMatchAllExpr(schema);
        List<String> outFields = buildMilvusScalarOutFields(schema);
        if (outFields.isEmpty()) {
            out.setHint("集合无可读的标量字段");
            return out;
        }
        long offset = 0L;
        if (cursor != null && !cursor.isBlank()) {
            try {
                offset = Long.parseLong(cursor.trim());
            } catch (NumberFormatException ignored) {
                offset = 0L;
            }
        }
        QueryParam qp = QueryParam.newBuilder()
                .withDatabaseName(cfg.databaseName())
                .withCollectionName(cn)
                .withExpr(expr)
                .withOutFields(outFields)
                .withLimit((long) lim)
                .withOffset(offset)
                .build();
        R<QueryResults> qr = client.query(qp);
        milvusAssertOk(qr, "query");
        QueryResults data = qr.getData();
        if (data == null) {
            return out;
        }
        QueryResultsWrapper wrapper = new QueryResultsWrapper(data);
        String pkName = findMilvusPrimaryKeyName(schema);
        for (QueryResultsWrapper.RowRecord rec : wrapper.getRowRecords()) {
            Map<String, Object> raw = rec.getFieldValues();
            if (raw == null) {
                continue;
            }
            Map<String, Object> payload = truncatePayloadForPreview(new LinkedHashMap<>(raw));
            VectorStorePointPreviewRowDto row = new VectorStorePointPreviewRowDto();
            if (pkName != null && raw.containsKey(pkName)) {
                row.setId(Objects.toString(raw.get(pkName), ""));
            } else if (!raw.isEmpty()) {
                row.setId(Objects.toString(raw.values().iterator().next(), ""));
            }
            row.setPayload(payload);
            out.getRows().add(row);
        }
        if (out.getRows().size() == lim) {
            out.setNextCursor(String.valueOf(offset + lim));
        }
        return out;
    }
}
