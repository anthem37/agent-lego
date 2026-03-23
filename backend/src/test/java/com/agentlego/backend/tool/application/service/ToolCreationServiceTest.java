package com.agentlego.backend.tool.application.service;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.tool.application.dto.CreateToolRequest;
import com.agentlego.backend.tool.application.support.ToolWriteSupport;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.agentlego.backend.tool.local.LocalBuiltinTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link ToolCreationService} 专注单测：与 {@link ToolApplicationServiceTest} 互补，覆盖 skip 重名校验等分支。
 */
@ExtendWith(MockitoExtension.class)
class ToolCreationServiceTest {

    @Mock
    private ToolRepository toolRepository;

    private static LocalBuiltinToolCatalog catalog() {
        return LocalBuiltinToolCatalog.forTests(LocalBuiltinTools.class);
    }

    private ToolCreationService service() {
        return new ToolCreationService(toolRepository, catalog(), new ToolWriteSupport(toolRepository));
    }

    @Test
    void createTool_skipNameCheck_shouldNotQueryGlobalUniqueness() {
        when(toolRepository.save(any(ToolAggregate.class))).thenAnswer(inv -> inv.getArgument(0, ToolAggregate.class).getId());

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType("HTTP");
        req.setName("any_name");
        req.setDefinition(Map.of("url", "https://example.com", "method", "GET"));

        service().createTool(req, true);

        verify(toolRepository, never()).existsOtherWithNameIgnoreCase(anyString(), any());
        verify(toolRepository, times(1)).save(any(ToolAggregate.class));
    }

    @Test
    void createTool_whenNotSkip_shouldCheckGlobalUniqueness() {
        when(toolRepository.existsOtherWithNameIgnoreCase("dup", null)).thenReturn(true);

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType("HTTP");
        req.setName("dup");
        req.setDefinition(Map.of("url", "https://example.com", "method", "GET"));

        assertThrows(ApiException.class, () -> service().createTool(req, false));
        verify(toolRepository, times(1)).existsOtherWithNameIgnoreCase("dup", null);
        verify(toolRepository, never()).save(any());
    }

    @Test
    void createTool_httpOk_shouldSave() {
        when(toolRepository.existsOtherWithNameIgnoreCase("api1", null)).thenReturn(false);
        when(toolRepository.save(any(ToolAggregate.class))).thenAnswer(inv -> inv.getArgument(0, ToolAggregate.class).getId());

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType("HTTP");
        req.setName("api1");
        req.setDefinition(Map.of("url", "https://a.com", "method", "GET"));

        String id = service().createTool(req);
        assertNotNull(id);
        verify(toolRepository, times(1)).save(any(ToolAggregate.class));
    }
}
