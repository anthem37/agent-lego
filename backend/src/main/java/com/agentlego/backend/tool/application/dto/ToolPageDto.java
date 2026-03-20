package com.agentlego.backend.tool.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 工具列表分页结果（GET /tools）。
 */
@Data
@Builder
public class ToolPageDto {

    private List<ToolDto> items;
    private long total;
    private int page;
    private int pageSize;
}
