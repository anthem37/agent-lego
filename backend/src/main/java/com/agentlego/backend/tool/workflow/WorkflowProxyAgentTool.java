package com.agentlego.backend.tool.workflow;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.schema.ToolOutputSchemaDescription;
import com.agentlego.backend.workflow.application.dto.RunWorkflowRequest;
import com.agentlego.backend.workflow.application.service.WorkflowApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将 {@link com.agentlego.backend.tool.domain.ToolType#WORKFLOW} 工具映射为可调用 {@link AgentTool}，
 * 同步执行工作流并把结果序列化为文本返回给模型。
 */
public final class WorkflowProxyAgentTool implements AgentTool {

    private static final Map<String, Object> DEFAULT_PARAMETERS;

    static {
        Map<String, Object> inputProp = Map.of(
                "type", "string",
                "description", "工作流主输入文本；也可用 JSON 任意字段，平台会序列化为字符串传入工作流。"
        );
        DEFAULT_PARAMETERS = Map.of(
                "type", "object",
                "properties", Map.of("input", inputProp),
                "required", List.of("input")
        );
    }

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;
    private final String workflowId;
    private final WorkflowApplicationService workflowApplicationService;
    private final ObjectMapper objectMapper;

    public WorkflowProxyAgentTool(
            ToolAggregate aggregate,
            WorkflowApplicationService workflowApplicationService,
            ObjectMapper objectMapper
    ) {
        this.workflowApplicationService = Objects.requireNonNull(workflowApplicationService, "workflowApplicationService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(aggregate, "aggregate");
        this.name = aggregate.getName();
        Map<String, Object> def = aggregate.getDefinition() == null ? Map.of() : aggregate.getDefinition();
        this.workflowId = readWorkflowId(def);
        this.description = describe(def);
        this.parameters = resolveParameters(def);
    }

    private static String readWorkflowId(Map<String, Object> def) {
        Object w = def.get("workflowId");
        if (w == null || String.valueOf(w).isBlank()) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "工作流工具 definition.workflowId 为必填",
                    HttpStatus.BAD_REQUEST
            );
        }
        return String.valueOf(w).trim();
    }

    private static String describe(Map<String, Object> def) {
        StringBuilder sb = new StringBuilder();
        Object d = def.get("description");
        if (d != null && !String.valueOf(d).isBlank()) {
            sb.append(String.valueOf(d).trim());
        } else {
            sb.append("Run workflow ").append(def.get("workflowId"));
        }
        ToolOutputSchemaDescription.appendToDescription(sb, def.get("outputSchema"));
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveParameters(Map<String, Object> def) {
        Object p = def.get("parameters");
        if (p instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        Object schema = def.get("inputSchema");
        if (schema instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return DEFAULT_PARAMETERS;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        return Mono.fromCallable(() -> invokeWorkflow(param, input))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock invokeWorkflow(ToolCallParam param, Map<String, Object> input) {
        try {
            String text = extractWorkflowInputText(input);
            RunWorkflowRequest req = new RunWorkflowRequest();
            req.setInput(text);

            Map<String, Object> result = workflowApplicationService.runWorkflowSynchronously(workflowId, req);
            String json = objectMapper.writeValueAsString(result);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("workflowId", workflowId);
            meta.put("runId", result.get("runId"));
            meta.put("status", result.get("status"));
            return ToolResultBlock.of(
                    param.getToolUseBlock().getId(),
                    param.getToolUseBlock().getName(),
                    io.agentscope.core.message.TextBlock.builder().text(json).build(),
                    meta
            );
        } catch (ApiException e) {
            return ToolResultBlock.error(e.getMessage());
        } catch (JsonProcessingException e) {
            return ToolResultBlock.error("Failed to serialize workflow result: " + e.getMessage());
        } catch (Exception e) {
            return ToolResultBlock.error("Workflow tool failed: " + e.getMessage());
        }
    }

    private String extractWorkflowInputText(Map<String, Object> input) throws JsonProcessingException {
        Object in = input.get("input");
        if (in instanceof String s && !s.isBlank()) {
            return s;
        }
        if (in != null && !(in instanceof String)) {
            return String.valueOf(in);
        }
        Object text = input.get("text");
        if (text != null && !String.valueOf(text).isBlank()) {
            return String.valueOf(text);
        }
        return objectMapper.writeValueAsString(input);
    }
}
