package com.agentlego.backend.model.application;

import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.model.dto.CreateModelRequest;
import com.agentlego.backend.model.dto.TestModelResponse;
import com.agentlego.backend.model.support.ChatModelFactory;
import com.agentlego.backend.model.support.ModelConnectivityTester;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private ModelApplicationService service;

    @Test
    void testModel_embeddingProvider_shouldSkipConnectionTest() {
        ModelAggregate agg = new ModelAggregate();
        agg.setId("m1");
        agg.setProvider("OPENAI_TEXT_EMBEDDING");
        agg.setModelKey("text-embedding-3-small");
        agg.setApiKeyCipher(null);
        agg.setBaseUrl(null);
        agg.setConfig(Map.of());

        when(modelRepository.findById("m1")).thenReturn(Optional.of(agg));

        TestModelResponse response = service.testModel("m1");
        assertEquals("EMBEDDING_TEST_SKIPPED", response.getMessage());
        assertEquals("EMBEDDING_TEST_SKIPPED", response.getRaw());

        verify(connectivityTester, never()).test(any());
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
        when(connectivityTester.test(model)).thenReturn("OK");

        TestModelResponse response = service.testModel("m1");
        assertEquals("OK", response.getMessage());
        assertEquals("OK", response.getRaw());
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
}

