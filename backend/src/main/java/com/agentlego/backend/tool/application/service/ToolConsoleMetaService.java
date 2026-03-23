package com.agentlego.backend.tool.application.service;

import com.agentlego.backend.tool.application.dto.LocalBuiltinToolMetaDto;
import com.agentlego.backend.tool.application.dto.ToolCategoryMetaDto;
import com.agentlego.backend.tool.application.dto.ToolTypeMetaDto;
import com.agentlego.backend.tool.domain.ToolCategory;
import com.agentlego.backend.tool.domain.ToolType;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 控制台元数据：工具类型 / 语义分类 / 内置 LOCAL 清单（与 CRUD、执行路径分离）。
 * <p>
 * LOCAL 下拉数据来自 {@link LocalBuiltinExposureApplicationService#listMetasForUi()}（受页面配置的 show_in_ui 影响）；
 * 类型说明中的「当前内置名」列表仍使用 {@link LocalBuiltinToolCatalog#listMeta()}（全量已注册内置）。
 */
@Service
public class ToolConsoleMetaService {

    /**
     * 多处复用的名称规则说明（与枚举 {@link ToolType} / DB 约束一致，仅存此处避免魔法字符串）。
     */
    private static final String TOOL_NAME_UNIQUENESS_RULE =
            "工具名将作为运行时注册名，须全平台唯一（大小写不敏感），并与模型侧工具调用约定一致。";

    private final LocalBuiltinToolCatalog localBuiltinToolCatalog;
    private final LocalBuiltinExposureApplicationService localBuiltinExposureApplicationService;

    public ToolConsoleMetaService(
            LocalBuiltinToolCatalog localBuiltinToolCatalog,
            LocalBuiltinExposureApplicationService localBuiltinExposureApplicationService
    ) {
        this.localBuiltinToolCatalog = localBuiltinToolCatalog;
        this.localBuiltinExposureApplicationService = localBuiltinExposureApplicationService;
    }

    public List<LocalBuiltinToolMetaDto> listLocalBuiltins() {
        return localBuiltinExposureApplicationService.listMetasForUi();
    }

    public List<ToolCategoryMetaDto> listToolCategoryMeta() {
        return List.of(
                ToolCategoryMetaDto.builder()
                        .code(ToolCategory.QUERY.name())
                        .label("查询工具")
                        .description("偏只读查询；知识库可将工具 JSON 出参字段映射到文档中的 {{占位符}}。")
                        .build(),
                ToolCategoryMetaDto.builder()
                        .code(ToolCategory.ACTION.name())
                        .label("操作工具")
                        .description("可能产生副作用或通用工具（默认分类）。")
                        .build()
        );
    }

    public List<ToolTypeMetaDto> listToolTypeMeta() {
        List<LocalBuiltinToolMetaDto> builtins = localBuiltinToolCatalog.listMeta();
        String localNames = builtins.stream()
                .map(LocalBuiltinToolMetaDto::getName)
                .collect(Collectors.joining("、"));
        String asNameRule = TOOL_NAME_UNIQUENESS_RULE;
        String localDescription = localNames.isBlank()
                ? "由后端 @Tool 宿主类注册；当前未发现内置实现。" + asNameRule
                : ("已注册内置：" + localNames + "。名称须与内置名一致；MCP/UI 暴露可在「内置工具暴露」中配置。" + asNameRule);
        return List.of(
                ToolTypeMetaDto.builder()
                        .code(ToolType.LOCAL.name())
                        .label("本地内置")
                        .description(localDescription)
                        .supportsTestCall(true)
                        .build(),
                ToolTypeMetaDto.builder()
                        .code(ToolType.HTTP.name())
                        .label("HTTP 请求")
                        .description("按 definition 调用外部 HTTP(S) API；出站请求使用 Square OkHttp（可配置超时，见 agentlego.tool.*），"
                                + "URL 经 SSRF 校验。"
                                + "definition.parameters / inputSchema 为 JSON Schema（常见工具协议子集），供模型理解与调用。"
                                + asNameRule)
                        .supportsTestCall(true)
                        .build(),
                ToolTypeMetaDto.builder()
                        .code(ToolType.WORKFLOW.name())
                        .label("工作流")
                        .description("绑定平台 workflowId，同步执行工作流；运行时作为可调用工具返回结构化结果。" + asNameRule)
                        .supportsTestCall(true)
                        .build(),
                ToolTypeMetaDto.builder()
                        .code(ToolType.MCP.name())
                        .label("MCP")
                        .description(
                                "登记外部 MCP Server（SSE URL 写入 definition.endpoint）；"
                                        + "可选 definition.mcpToolName 指定远端工具名（默认与平台工具 name 一致）。"
                                        + "可使用 GET /tools/meta/mcp/remote-tools 发现远端工具列表，"
                                        + "POST /tools/meta/mcp/batch-import 批量导入（默认同名记入 nameConflicts 可改名重试；skipExisting=true 则跳过）。"
                                        + "本服务同时对外暴露 MCP（见 agentlego.mcp.server.sse-path，默认同源 /mcp）；"
                                        + "本机内置工具是否在 MCP 中列出可在「内置工具暴露」中配置。"
                                        + "SSRF：agentlego.mcp.client.strict-ssrf=true 时与 HTTP 工具一致禁止内网地址。"
                                        + "入参 Schema 建议与远端 MCP 工具定义一致。"
                                        + asNameRule
                        )
                        .supportsTestCall(true)
                        .build()
        );
    }
}
