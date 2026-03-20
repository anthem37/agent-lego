package com.agentlego.backend.common;

import cn.hutool.core.util.IdUtil;

/**
 * 基于 Hutool 的 Snowflake ID 生成器。
 * <p>
 * 设计说明：
 * - 对外统一暴露为 String，避免前端（JavaScript Number）精度丢失；
 * - 数据库字段使用 VARCHAR(32)，承载 Snowflake long 的字符串形式。
 */
public final class SnowflakeIdGenerator {
    private SnowflakeIdGenerator() {
    }

    /**
     * 生成下一个 ID（String）。
     */
    public static String nextId() {
        return IdUtil.getSnowflakeNextIdStr();
    }
}

