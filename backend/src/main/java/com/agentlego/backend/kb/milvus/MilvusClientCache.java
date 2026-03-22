package com.agentlego.backend.kb.milvus;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按连接参数复用 {@link MilvusServiceClient}，避免多集合重复建连。
 */
@Component
public class MilvusClientCache implements DisposableBean {

    private final Map<String, MilvusServiceClient> clients = new ConcurrentHashMap<>();

    private static MilvusServiceClient open(KbMilvusSettings s) {
        ConnectParam.Builder b = ConnectParam.newBuilder()
                .withHost(s.host())
                .withPort(s.port())
                .withDatabaseName(s.databaseName())
                .secure(s.secure());
        if (s.token() != null && !s.token().isEmpty()) {
            b.withToken(s.token());
        } else if (s.username() != null && s.password() != null) {
            b.withAuthorization(s.username(), s.password());
        }
        return new MilvusServiceClient(b.build());
    }

    public MilvusServiceClient client(KbMilvusSettings s) {
        String key = s.cacheKey();
        return clients.computeIfAbsent(key, k -> open(s));
    }

    @Override
    public void destroy() {
        clients.values().forEach(c -> {
            try {
                c.close(3);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        clients.clear();
    }
}
