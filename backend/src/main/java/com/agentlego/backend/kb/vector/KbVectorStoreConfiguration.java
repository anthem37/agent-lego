package com.agentlego.backend.kb.vector;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class KbVectorStoreConfiguration {

    @Bean(name = "kbVectorStoreHttpClient")
    public OkHttpClient kbVectorStoreHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(120))
                .callTimeout(Duration.ofSeconds(180))
                .build();
    }
}
