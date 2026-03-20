package com.agentlego.backend.runtime.definition;

import java.util.Map;

public record ModelDefinition(
        String provider,   // e.g. "DASHSCOPE", "OPENAI"
        String modelName,  // provider-specific model id/name
        String apiKey,      // may be null/empty if provider uses other auth flows
        String baseUrl,     // optional provider base url / gateway
        Map<String, Object> config // provider/default generate options
) {
}

