package com.agentlego.backend.model.application.assembler;

import com.agentlego.backend.model.application.dto.ModelDto;
import com.agentlego.backend.model.application.dto.ModelSummaryDto;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.support.ModelConfigSummaries;

/**
 * 模型聚合 → 查询 DTO（Application 层）。
 */
public final class ModelAssembler {

    private ModelAssembler() {
    }

    public static ModelDto toDto(ModelAggregate agg) {
        if (agg == null) {
            return null;
        }
        ModelDto dto = new ModelDto();
        dto.setId(agg.getId());
        dto.setName(agg.getName());
        dto.setDescription(agg.getDescription());
        dto.setProvider(agg.getProvider());
        dto.setModelKey(agg.getModelKey());
        dto.setConfig(agg.getConfig());
        dto.setBaseUrl(agg.getBaseUrl());
        dto.setCreatedAt(agg.getCreatedAt());
        String cipher = agg.getApiKeyCipher();
        dto.setApiKeyConfigured(cipher != null && !cipher.isBlank());
        return dto;
    }

    public static ModelSummaryDto toSummaryDto(ModelAggregate agg) {
        if (agg == null) {
            return null;
        }
        ModelSummaryDto dto = new ModelSummaryDto();
        dto.setId(agg.getId());
        dto.setName(agg.getName());
        dto.setDescription(agg.getDescription());
        dto.setProvider(agg.getProvider());
        dto.setModelKey(agg.getModelKey());
        dto.setBaseUrl(agg.getBaseUrl());
        dto.setConfigSummary(ModelConfigSummaries.summarize(agg.getConfig()));
        dto.setCreatedAt(agg.getCreatedAt());
        return dto;
    }
}
