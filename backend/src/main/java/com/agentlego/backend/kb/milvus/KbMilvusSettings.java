package com.agentlego.backend.kb.milvus;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Map;

/**
 * 解析 {@code lego_kb_collections.vector_store_config}（MILVUS）。
 */
public record KbMilvusSettings(
        String host,
        int port,
        boolean secure,
        String token,
        String username,
        String password,
        String databaseName,
        String collectionName,
        MetricType metricType,
        IndexType indexType,
        String indexExtraParams
) {
    public static KbMilvusSettings fromConfigMap(Map<String, Object> raw) {
        return fromConfigMap(raw, true);
    }

    /**
     * @param requireCollectionName false：用于公共向量库 profile 仅配置连接；物理 collection 名在知识库创建或记忆写入时指定。
     */
    public static KbMilvusSettings fromConfigMap(Map<String, Object> raw, boolean requireCollectionName) {
        if (raw == null || raw.isEmpty()) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "vectorStoreConfig 不能为空：请提供 host、collectionName 等（创建集合时必填）",
                    HttpStatus.BAD_REQUEST
            );
        }
        String host = JsonMaps.getString(raw, "host", null);
        if (host == null || host.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "vectorStoreConfig.host 为必填", HttpStatus.BAD_REQUEST);
        }
        host = host.trim();
        int port = JsonMaps.getIntOpt(raw, "port");
        if (port <= 0) {
            port = 19530;
        }
        boolean secure = Boolean.TRUE.equals(raw.get("secure"));
        String token = JsonMaps.getString(raw, "token", null);
        if (token != null) {
            token = token.trim();
            if (token.isEmpty()) {
                token = null;
            }
        }
        String user = JsonMaps.getString(raw, "username", null);
        if (user == null) {
            user = JsonMaps.getString(raw, "user", null);
        }
        if (user != null) {
            user = user.trim();
            if (user.isEmpty()) {
                user = null;
            }
        }
        String password = JsonMaps.getString(raw, "password", null);
        if (password != null) {
            password = password.trim();
            if (password.isEmpty()) {
                password = null;
            }
        }
        String db = JsonMaps.getString(raw, "database", null);
        if (db == null) {
            db = JsonMaps.getString(raw, "dbName", null);
        }
        if (db == null || db.isBlank()) {
            db = "default";
        } else {
            db = db.trim();
        }
        String coll = JsonMaps.getString(raw, "collectionName", null);
        if (coll == null) {
            coll = JsonMaps.getString(raw, "collection", null);
        }
        if (coll == null || coll.isBlank()) {
            if (requireCollectionName) {
                throw new ApiException("VALIDATION_ERROR", "vectorStoreConfig.collectionName 为必填", HttpStatus.BAD_REQUEST);
            }
            coll = "";
        } else {
            coll = coll.trim();
            if (!coll.matches("^[a-zA-Z0-9_]{1,255}$")) {
                throw new ApiException(
                        "VALIDATION_ERROR",
                        "vectorStoreConfig.collectionName 仅允许字母数字与下划线",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
        MetricType metric = parseMetric(JsonMaps.getString(raw, "metricType", null));
        IndexType index = parseIndex(JsonMaps.getString(raw, "indexType", null));
        String extra = JsonMaps.getString(raw, "indexParams", null);
        if (extra == null || extra.isBlank()) {
            extra = defaultIndexExtra(index);
        } else {
            extra = extra.trim();
        }
        return new KbMilvusSettings(host, port, secure, token, user, password, db, coll, metric, index, extra);
    }

    private static String defaultIndexExtra(IndexType index) {
        if (index == IndexType.IVF_FLAT || index == IndexType.IVF_SQ8) {
            return "{\"nlist\":1024}";
        }
        if (index == IndexType.HNSW) {
            return "{\"M\":8,\"efConstruction\":200}";
        }
        return "{}";
    }

    private static MetricType parseMetric(String raw) {
        if (raw == null || raw.isBlank()) {
            return MetricType.COSINE;
        }
        try {
            return MetricType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", "不支持的 metricType: " + raw, HttpStatus.BAD_REQUEST);
        }
    }

    private static IndexType parseIndex(String raw) {
        if (raw == null || raw.isBlank()) {
            return IndexType.IVF_FLAT;
        }
        try {
            return IndexType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", "不支持的 indexType: " + raw, HttpStatus.BAD_REQUEST);
        }
    }

    public String cacheKey() {
        String t = token == null ? "" : token;
        String u = username == null ? "" : username;
        String p = password == null ? "" : password;
        return host + "|" + port + "|" + secure + "|" + databaseName + "|" + t + "|" + u + "|" + p;
    }
}
