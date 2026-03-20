package com.agentlego.backend.memory;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.memory.application.MemoryApplicationService;
import com.agentlego.backend.memory.application.dto.CreateMemoryItemRequest;
import com.agentlego.backend.memory.application.dto.MemoryItemDto;
import com.agentlego.backend.memory.application.dto.MemoryQueryRequest;
import com.agentlego.backend.memory.application.dto.MemoryQueryResponse;
import com.agentlego.backend.memory.domain.MemoryItemAggregate;
import com.agentlego.backend.memory.domain.MemoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MemoryApplicationService 单元测试。
 * <p>
 * 覆盖点：
 * - createItem 参数校验（空 content）
 * - metadata 为空的兜底行为
 * - query 的参数透传与 DTO 映射
 */
@ExtendWith(MockitoExtension.class)
class MemoryApplicationServiceTest {

    @Mock
    private MemoryRepository repository;

    @Test
    void createItem_blankContent_shouldThrowValidationError() {
        MemoryApplicationService service = new MemoryApplicationService(repository);

        CreateMemoryItemRequest req = new CreateMemoryItemRequest();
        req.setOwnerScope("user1");
        req.setContent("   ");
        req.setMetadata(Map.of("k", "v"));

        ApiException ex = assertThrows(ApiException.class, () -> service.createItem(req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void createItem_nullMetadata_shouldStoreEmptyMap() {
        MemoryApplicationService service = new MemoryApplicationService(repository);

        CreateMemoryItemRequest req = new CreateMemoryItemRequest();
        req.setOwnerScope("user1");
        req.setContent("hello");
        req.setMetadata(null);

        when(repository.save(any())).thenReturn("mem1");

        String id = service.createItem(req);
        assertEquals("mem1", id);

        ArgumentCaptor<MemoryItemAggregate> captor = ArgumentCaptor.forClass(MemoryItemAggregate.class);
        verify(repository).save(captor.capture());
        assertEquals("user1", captor.getValue().getOwnerScope());
        assertNotNull(captor.getValue().getMetadata());
        assertTrue(captor.getValue().getMetadata().isEmpty());
    }

    @Test
    void query_shouldPassThroughTopKAndMapToDto() {
        MemoryApplicationService service = new MemoryApplicationService(repository);

        MemoryQueryRequest req = new MemoryQueryRequest();
        req.setOwnerScope("user1");
        req.setQueryText("hi");
        req.setTopK(2);

        MemoryItemAggregate item = new MemoryItemAggregate();
        item.setId("mem1");
        item.setOwnerScope("user1");
        item.setContent("hello");
        item.setMetadata(Map.of("a", 1));
        item.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));

        when(repository.search(eq("user1"), eq("hi"), eq(2))).thenReturn(List.of(item));

        MemoryQueryResponse resp = service.query(req);
        assertNotNull(resp);
        assertNotNull(resp.getItems());
        assertEquals(1, resp.getItems().size());

        MemoryItemDto dto = resp.getItems().get(0);
        assertEquals("mem1", dto.getId());
        assertEquals("hello", dto.getContent());
    }
}

