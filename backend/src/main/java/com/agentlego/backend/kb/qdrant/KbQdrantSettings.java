package com.agentlego.backend.kb.qdrant;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Map;

/**
 * 解析 {@code lego_kb_collections.vector_store_config}（QDRANT，REST）。
 */
public record KbQdrantSettings(
        String baseUrl,
        String apiKey,
        String collectionName,
        QdrantDistance distance
) {
    public static KbQdrantSettings fromConfigMap(Map<String, Object> raw) {
        return fromConfigMap(raw, true);
    }

    /**
     * @param requireCollectionName false：公共向量库 profile 仅配置连接时可省略 collectionName。
     */
    public static KbQdrantSettings fromConfigMap(Map<String, Object> raw, boolean requireCollectionName) {
        if (raw == null || raw.isEmpty()) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "vectorStoreConfig 不能为空：请提供 url 或 host、collectionName 等（创建集合时必填）",
                    HttpStatus.BAD_REQUEST
            );
        }
        String url = JsonMaps.getString(raw, "url", null);
        if (url != null) {
            url = url.trim();
            if (url.isEmpty()) {
                url = null;
            }
        }
        String host = JsonMaps.getString(raw, "host", null);
        if (host != null) {
            host = host.trim();
            if (host.isEmpty()) {
                host = null;
            }
        }
        String baseUrl;
        if (url != null) {
            baseUrl = stripTrailingSlash(url);
        } else if (host != null) {
            int port = JsonMaps.getIntOpt(raw, "port");
            if (port <= 0) {
                port = 6333;
            }
            boolean secure = Boolean.TRUE.equals(raw.get("secure")) || Boolean.TRUE.equals(raw.get("tls"));
            baseUrl = (secure ? "https://" : "http://") + host + ":" + port;
        } else {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "vectorStoreConfig.url 或 vectorStoreConfig.host 为必填（Qdrant）",
                    HttpStatus.BAD_REQUEST
            );
        }
        String apiKey = JsonMaps.getString(raw, "apiKey", null);
        if (apiKey == null) {
            apiKey = JsonMaps.getString(raw, "api_key", null);
        }
        if (apiKey != null) {
            apiKey = apiKey.trim();
            if (apiKey.isEmpty()) {
                apiKey = null;
            }
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
            if (!coll.matches("^[a-zA-Z0-9_\\-]{1,255}$")) {
                throw new ApiException(
                        "VALIDATION_ERROR",
                        "vectorStoreConfig.collectionName 仅允许字母数字、下划线与连字符",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
        QdrantDistance dist = parseDistance(JsonMaps.getString(raw, "distance", null));
        return new KbQdrantSettings(baseUrl, apiKey, coll, dist);
    }

    private static String stripTrailingSlash(String u) {
        String s = u;
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static QdrantDistance parseDistance(String raw) {
        if (raw == null || raw.isBlank()) {
            return QdrantDistance.COSINE;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "COSINE" -> QdrantDistance.COSINE;
            case "DOT", "DOT_PRODUCT", "IP", "INNER_PRODUCT" -> QdrantDistance.DOT;
            case "EUCLID", "EUCLIDEAN", "L2" -> QdrantDistance.EUCLID;
            default -> throw new ApiException("VALIDATION_ERROR", "不支持的 distance: " + raw, HttpStatus.BAD_REQUEST);
        };
    }

    public String qdrantDistanceName() {
        return switch (distance) {
            case COSINE -> "Cosine";
            case DOT -> "Dot";
            case EUCLID -> "Euclid";
        };
    }

    public enum QdrantDistance {
        COSINE,
        DOT,
        EUCLID
    }
}
