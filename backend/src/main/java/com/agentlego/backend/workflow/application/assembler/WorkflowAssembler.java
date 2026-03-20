package com.agentlego.backend.workflow.application.assembler;

import com.agentlego.backend.workflow.application.dto.WorkflowDto;
import com.agentlego.backend.workflow.application.dto.WorkflowRunDto;
import com.agentlego.backend.workflow.domain.WorkflowAggregate;
import com.agentlego.backend.workflow.domain.WorkflowRunAggregate;

/**
 * 工作流领域对象 → 对外 DTO 的装配（Application 层，无框架依赖）。
 * <p>
 * 集中 setter 映射，避免 {@code WorkflowApplicationService} 与 Controller 多处重复。
 */
public final class WorkflowAssembler {

    private WorkflowAssembler() {
    }

    public static WorkflowDto toWorkflowDto(WorkflowAggregate agg) {
        if (agg == null) {
            return null;
        }
        WorkflowDto dto = new WorkflowDto();
        dto.setId(agg.getId());
        dto.setName(agg.getName());
        dto.setDefinition(agg.getDefinition());
        dto.setCreatedAt(agg.getCreatedAt());
        return dto;
    }

    public static WorkflowRunDto toRunDto(WorkflowRunAggregate run) {
        if (run == null) {
            return null;
        }
        WorkflowRunDto dto = new WorkflowRunDto();
        dto.setId(run.getId());
        dto.setWorkflowId(run.getWorkflowId());
        dto.setStatus(run.getStatus() == null ? null : run.getStatus().name());
        dto.setInput(run.getInput());
        dto.setOutput(run.getOutput());
        dto.setError(run.getError());
        dto.setStartedAt(run.getStartedAt());
        dto.setFinishedAt(run.getFinishedAt());
        dto.setCreatedAt(run.getCreatedAt());
        return dto;
    }
}
