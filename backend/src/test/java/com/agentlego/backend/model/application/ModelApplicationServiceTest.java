package com.agentlego.backend.model.application;

import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.model.application.dto.CreateModelRequest;
import com.agentlego.backend.model.application.dto.TestModelResponse;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.model.support.ChatModelFactory;
import com.agentlego.backend.model.support.ModelConnectivityTester;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelApplicationServiceTest {

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private ModelConnectivityTester connectivityTester;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ModelEmbeddingClient modelEmbeddingClient;

    @InjectMocks
    private ModelApplicationService service;

    @Test
    void testModel_embeddingProvider_shouldRunEmbedProbe() {
        ModelAggregate agg = new ModelAggregate();
        agg.setId("m1");
        agg.setProvider("OPENAI_TEXT_EMBEDDING");
        agg.setModelKey("text-embedding-3-small");
        agg.setApiKeyCipher(null);
        agg.setBaseUrl(null);
        agg.setConfig(Map.of());

        when(modelRepository.findById("m1")).thenReturn(Optional.of(agg));
        when(modelEmbeddingClient.embed(eq("m1"), anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f, 0.3f}));

        TestModelResponse response = service.testModel("m1");
        assertEquals("EMBEDDING", response.getTestType());
        assertEquals("OK", response.getStatus());
        assertEquals(3, response.getEmbeddingDimension());
        assertNotNull(response.getLatencyMs());

        verify(modelEmbeddingClient).embed(eq("m1"), anyList());
        verify(connectivityTester, never()).testChat(any(), any(), any(), any());
        verify(chatModelFactory, never()).from(any(ModelDefinition.class));
    }

    @Test
    void testModel_unsupportedProvider_shouldThrowApiException() {
        ModelAggregate agg = new ModelAggregate();
        agg.setId("m1");
        agg.setProvider("BAD_PROVIDER");
        agg.setModelKey("x");
        agg.setApiKeyCipher(null);
        agg.setBaseUrl(null);
        agg.setConfig(Map.of());

        when(modelRepository.findById("m1")).thenReturn(Optional.of(agg));

        ApiException ex = assertThrows(ApiException.class, () -> service.testModel("m1"));
        assertEquals("UNSUPPORTED_MODEL_PROVIDER", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void testModel_chatProvider_shouldUseConnectivityTester() {
        ModelAggregate agg = new ModelAggregate();
        agg.setId("m1");
        agg.setProvider("DASHSCOPE");
        agg.setModelKey("qwen-max");
        agg.setApiKeyCipher("k");
        agg.setBaseUrl(null);
        agg.setConfig(Map.of());

        when(modelRepository.findById("m1")).thenReturn(Optional.of(agg));

        Model model = Mockito.mock(Model.class);
        when(chatModelFactory.from(any(ModelDefinition.class))).thenReturn(model);
        when(connectivityTester.testChat(eq(model), isNull(), isNull(), isNull()))
                .thenReturn(new ModelConnectivityTester.ChatConnectivityResult(
                        "OK",
                        15L,
                        2,
                        null,
                        "Reply with a single word: OK.",
                        256
                ));

        TestModelResponse response = service.testModel("m1");
        assertEquals("CHAT", response.getTestType());
        assertEquals("OK", response.getStatus());
        assertEquals(15L, response.getLatencyMs());
        assertEquals(2, response.getStreamChunks());
        assertEquals("OK", response.getMessage());
        assertEquals("OK", response.getRaw());
        verify(connectivityTester).testChat(eq(model), isNull(), isNull(), isNull());
    }

    @Test
    void createModel_unsupportedConfigKey_shouldThrowApiException() {
        CreateModelRequest req = new CreateModelRequest();
        req.setName("n");
        req.setProvider("DASHSCOPE");
        req.setModelKey("qwen-max");
        req.setApiKey("k");
        req.setDescription("d");
        req.setBaseUrl(null);
        req.setConfig(Map.of("not_supported_key", 1));

        ApiException ex = assertThrows(ApiException.class, () -> service.createModel(req));
        assertEquals("INVALID_MODEL_CONFIG", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());

        verify(modelRepository, never()).save(any());
    }

    @Test
    void createModel_withAgentScopeExtendedConfig_shouldSucceed() {
        CreateModelRequest req = new CreateModelRequest();
        req.setName("n");
        req.setProvider("OPENAI");
        req.setModelKey("gpt-4o-mini");
        req.setApiKey("k");
        req.setConfig(Map.of(
                "stream", true,
                "frequencyPenalty", 0.1,
                "presencePenalty", 0.0,
                "toolChoice", "auto",
                "executionConfig", Map.of(
                        "timeoutSeconds", 60.0,
                        "maxAttempts", 3,
                        "initialBackoffSeconds", 0.5,
                        "maxBackoffSeconds", 8.0,
                        "backoffMultiplier", 2.0
                )
        ));

        when(modelRepository.save(any(ModelAggregate.class))).thenReturn("id1");

        String id = service.createModel(req);
        assertEquals("id1", id);
        verify(modelRepository).save(any(ModelAggregate.class));
    }

    @Test
    void createModel_invalidExecutionConfigKey_shouldThrowApiException() {
        CreateModelRequest req = new CreateModelRequest();
        req.setName("n");
        req.setProvider("OPENAI");
        req.setModelKey("gpt-4o-mini");
        req.setApiKey("k");
        req.setConfig(Map.of("executionConfig", Map.of("unknownKey", 1)));

        ApiException ex = assertThrows(ApiException.class, () -> service.createModel(req));
        assertEquals("INVALID_MODEL_CONFIG", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(modelRepository, never()).save(any());
    }

    @Test
    void createModel_toolChoiceSpecificWithoutToolName_shouldThrowApiException() {
        CreateModelRequest req = new CreateModelRequest();
        req.setName("n");
        req.setProvider("OPENAI");
        req.setModelKey("gpt-4o-mini");
        req.setApiKey("k");
        req.setConfig(Map.of("toolChoice", Map.of("mode", "specific")));

        ApiException ex = assertThrows(ApiException.class, () -> service.createModel(req));
        assertEquals("INVALID_MODEL_CONFIG", ex.getCode());
        verify(modelRepository, never()).save(any());
    }
}

