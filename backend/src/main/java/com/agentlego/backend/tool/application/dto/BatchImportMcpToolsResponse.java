package com.agentlego.backend.tool.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchImportMcpToolsResponse {

    private List<Created> created;
    private List<Skipped> skipped;

    @Data
    @Builder
    public static class Created {
        private String id;
        /**
         * 平台工具 name
         */
        private String name;
        private String remoteToolName;
    }

    @Data
    @Builder
    public static class Skipped {
        /**
         * 拟创建的平台名或远端名（便于排查）
         */
        private String name;
        private String reason;
    }
}
