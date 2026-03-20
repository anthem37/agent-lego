package com.agentlego.backend.eval.application.assembler;

import com.agentlego.backend.eval.application.dto.RunEvaluationDto;
import com.agentlego.backend.eval.domain.EvaluationRunAggregate;

/**
 * 评测运行聚合 → 查询 DTO（Application 层装配器）。
 */
public final class EvaluationAssembler {

    private EvaluationAssembler() {
    }

    public static RunEvaluationDto toRunDto(EvaluationRunAggregate run) {
        if (run == null) {
            return null;
        }
        RunEvaluationDto dto = new RunEvaluationDto();
        dto.setId(run.getId());
        dto.setEvaluationId(run.getEvaluationId());
        dto.setStatus(run.getStatus() == null ? null : run.getStatus().name());
        dto.setInput(run.getInput());
        dto.setMetrics(run.getMetrics());
        dto.setTrace(run.getTrace());
        dto.setError(run.getError());
        dto.setStartedAt(run.getStartedAt());
        dto.setFinishedAt(run.getFinishedAt());
        dto.setCreatedAt(run.getCreatedAt());
        return dto;
    }
}
