package com.agentlego.backend.tool.application.service;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.tool.application.dto.LocalBuiltinExposureItemDto;
import com.agentlego.backend.tool.application.dto.LocalBuiltinExposureRowDto;
import com.agentlego.backend.tool.application.dto.LocalBuiltinToolMetaDto;
import com.agentlego.backend.tool.application.dto.UpdateLocalBuiltinExposureRequest;
import com.agentlego.backend.tool.infrastructure.persistence.LocalBuiltinExposureDO;
import com.agentlego.backend.tool.infrastructure.persistence.LocalBuiltinExposureMapper;
import com.agentlego.backend.mcp.config.McpBuiltinToolsSynchronizer;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 内置 LOCAL 工具在 MCP / 管理端 UI 的暴露策略（持久化于 {@code lego_local_builtin_tool_exposure}）。
 * <p>
 * 工具清单来自 {@link LocalBuiltinToolCatalog} 聚合的<strong>已注册内置</strong>；启动时为每个内置工具名补默认行（全开启）。
 */
@Service
public class LocalBuiltinExposureApplicationService {

    private final LocalBuiltinToolCatalog localBuiltinToolCatalog;
    private final LocalBuiltinExposureMapper localBuiltinExposureMapper;
    private final ObjectProvider<McpBuiltinToolsSynchronizer> mcpBuiltinToolsSynchronizer;

    public LocalBuiltinExposureApplicationService(
            LocalBuiltinToolCatalog localBuiltinToolCatalog,
            LocalBuiltinExposureMapper localBuiltinExposureMapper,
            ObjectProvider<McpBuiltinToolsSynchronizer> mcpBuiltinToolsSynchronizer
    ) {
        this.localBuiltinToolCatalog = localBuiltinToolCatalog;
        this.localBuiltinExposureMapper = localBuiltinExposureMapper;
        this.mcpBuiltinToolsSynchronizer = mcpBuiltinToolsSynchronizer;
    }

    @PostConstruct
    public void ensureExposureRowsForDiscoveredTools() {
        for (LocalBuiltinToolMetaDto m : localBuiltinToolCatalog.listMeta()) {
            localBuiltinExposureMapper.insertIfAbsent(m.getName().trim());
        }
    }

    /**
     * 管理端配置页：已注册内置及当前开关。
     */
    public List<LocalBuiltinExposureRowDto> listExposureSettings() {
        Map<String, LocalBuiltinExposureDO> byLower = localBuiltinExposureMapper.selectAll().stream()
                .collect(Collectors.toMap(
                        r -> r.getToolName().trim().toLowerCase(Locale.ROOT),
                        r -> r,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        return localBuiltinToolCatalog.listMeta().stream()
                .map(meta -> {
                    String key = meta.getName().trim().toLowerCase(Locale.ROOT);
                    LocalBuiltinExposureDO row = byLower.get(key);
                    boolean mcp = row == null || row.isExposeMcp();
                    boolean ui = row == null || row.isShowInUi();
                    return LocalBuiltinExposureRowDto.builder()
                            .name(meta.getName())
                            .label(meta.getLabel())
                            .description(meta.getDescription())
                            .exposeMcp(mcp)
                            .showInUi(ui)
                            .build();
                })
                .toList();
    }

    /**
     * 「创建 LOCAL 工具」等下拉：仅返回允许在 UI 展示的内置名。
     */
    public List<LocalBuiltinToolMetaDto> listMetasForUi() {
        Map<String, LocalBuiltinExposureDO> byLower = loadExposureByLowerName();
        return localBuiltinToolCatalog.listMeta().stream()
                .filter(meta -> {
                    String key = meta.getName().trim().toLowerCase(Locale.ROOT);
                    LocalBuiltinExposureDO row = byLower.get(key);
                    return row == null || row.isShowInUi();
                })
                .toList();
    }

    /**
     * 本机 MCP Server 注册 tools：仅暴露用户允许的工具。
     */
    public List<LocalBuiltinToolMetaDto> listMetasForMcp() {
        Map<String, LocalBuiltinExposureDO> byLower = loadExposureByLowerName();
        return localBuiltinToolCatalog.listMeta().stream()
                .filter(meta -> {
                    String key = meta.getName().trim().toLowerCase(Locale.ROOT);
                    LocalBuiltinExposureDO row = byLower.get(key);
                    return row == null || row.isExposeMcp();
                })
                .toList();
    }

    private Map<String, LocalBuiltinExposureDO> loadExposureByLowerName() {
        return localBuiltinExposureMapper.selectAll().stream()
                .collect(Collectors.toMap(
                        r -> r.getToolName().trim().toLowerCase(Locale.ROOT),
                        r -> r,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    @Transactional
    public void updateExposure(UpdateLocalBuiltinExposureRequest req) {
        if (req == null || req.getItems() == null) {
            throw new ApiException("VALIDATION_ERROR", "请求体不能为空", HttpStatus.BAD_REQUEST);
        }
        var allowed = localBuiltinToolCatalog.listMeta().stream()
                .map(m -> m.getName().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (LocalBuiltinExposureItemDto it : req.getItems()) {
            String name = ApiRequires.nonBlank(it.getToolName(), "toolName").trim();
            String key = name.toLowerCase(Locale.ROOT);
            if (!allowed.contains(key)) {
                throw new ApiException(
                        "VALIDATION_ERROR",
                        "未知内置工具名（须为当前已注册内置之一）：" + name,
                        HttpStatus.BAD_REQUEST
                );
            }
            Boolean mcp = Objects.requireNonNull(it.getExposeMcp(), "exposeMcp");
            Boolean ui = Objects.requireNonNull(it.getShowInUi(), "showInUi");
            localBuiltinExposureMapper.update(name, mcp, ui);
        }
        mcpBuiltinToolsSynchronizer.ifAvailable(sync -> sync.syncTo(listMetasForMcp()));
    }
}
