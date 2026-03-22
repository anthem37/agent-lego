package com.agentlego.backend.vectorstore.web;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.vectorstore.application.dto.*;
import com.agentlego.backend.vectorstore.application.service.VectorStoreOperationsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 向量库常用运维：探测、列集合、统计、嵌入自检、加载/删除物理 collection 等。
 */
@RestController
@RequestMapping("/vector-store-profiles")
public class VectorStoreOperationsController {

    private final VectorStoreOperationsService operationsService;

    public VectorStoreOperationsController(VectorStoreOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping("/{id}/usage")
    public ApiResponse<VectorStoreUsageDto> usage(@PathVariable String id) {
        return ApiResponse.ok(operationsService.usage(id));
    }

    @GetMapping("/{id}/probe")
    public ApiResponse<VectorStoreProbeResultDto> probe(@PathVariable String id) {
        return ApiResponse.ok(operationsService.probe(id));
    }

    @GetMapping("/{id}/collections")
    public ApiResponse<List<VectorStoreCollectionSummaryDto>> listCollections(@PathVariable String id) {
        return ApiResponse.ok(operationsService.listCollections(id));
    }

    @GetMapping("/{id}/collection-stats")
    public ApiResponse<VectorStoreCollectionStatsDto> collectionStats(
            @PathVariable String id,
            @RequestParam("collectionName") String collectionName
    ) {
        return ApiResponse.ok(operationsService.collectionStats(id, collectionName));
    }

    @PostMapping("/{id}/load-collection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void loadCollection(
            @PathVariable String id,
            @RequestParam("collectionName") String collectionName
    ) {
        operationsService.loadCollection(id, collectionName);
    }

    @PostMapping("/{id}/drop-collection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dropCollection(
            @PathVariable String id,
            @Valid @RequestBody DropVectorStoreCollectionRequest req
    ) {
        operationsService.dropPhysicalCollection(id, req);
    }

    @PostMapping("/{id}/embedding-probe")
    public ApiResponse<VectorStoreEmbeddingProbeResultDto> embeddingProbe(
            @PathVariable String id,
            @Valid @RequestBody VectorStoreEmbeddingProbeRequest req
    ) {
        return ApiResponse.ok(operationsService.probeEmbedding(id, req));
    }

    /**
     * Qdrant：scroll 抽样预览点 payload；Milvus 返回 hint
     */
    @GetMapping("/{id}/points-preview")
    public ApiResponse<VectorStorePointsPreviewDto> pointsPreview(
            @PathVariable String id,
            @RequestParam("collectionName") String collectionName,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        return ApiResponse.ok(operationsService.pointsPreview(id, collectionName, limit, cursor));
    }
}
