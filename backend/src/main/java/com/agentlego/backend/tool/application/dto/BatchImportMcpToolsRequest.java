package com.agentlego.backend.tool.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 按远端工具名批量登记平台 MCP 工具。
 */
@Data
public class BatchImportMcpToolsRequest {

    /**
     * 外部 MCP SSE 根 URL。
     */
    @NotBlank(message = "endpoint 为必填")
    private String endpoint;

    /**
     * 要导入的远端工具名；{@code null} 或空列表表示导入远端全部工具。
     */
    private List<String> remoteToolNames;

    /**
     * 平台工具名前缀（可选），实际名称为 {@code prefix + 远端工具名}（经服务端清洗规则处理）。
     */
    private String namePrefix;

    /**
     * 按远端工具名覆盖拟创建的平台 {@code name}（可选）。key 为远端 {@code tools/list} 中的工具名，须与
     * {@link #remoteToolNames} 或远端列表一致；value 为平台工具名（trim 后使用）。未出现的远端工具仍按前缀+清洗规则生成。
     */
    private Map<String, String> platformNamesByRemote;

    /**
     * 为 true 时：与已有工具全平台重名则记入 {@code skipped} 并继续其它条目。<br>
     * 为 false 或省略时：重名记入响应中的 {@code nameConflicts}，便于前端提示用户改
     * {@link #platformNamesByRemote} 后重试；不再为此抛 409。
     */
    private Boolean skipExisting;
}
