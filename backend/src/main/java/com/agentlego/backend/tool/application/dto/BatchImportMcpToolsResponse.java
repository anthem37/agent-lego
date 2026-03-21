package com.agentlego.backend.tool.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchImportMcpToolsResponse {

    private List<Created> created;
    private List<Skipped> skipped;
    /**
     * 当 {@code skipExisting} 为 false/省略且拟创建的平台名与已有工具（或本批已创建）冲突时返回，便于用户改名后重试。
     */
    private List<NameConflict> nameConflicts;

    @Data
    @Builder
    public static class NameConflict {
        private String remoteToolName;
        private String attemptedPlatformName;
        private String reason;
    }

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
