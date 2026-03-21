package com.agentlego.backend.common;

/**
 * 枚举与字符串互转（MapStruct / DTO 映射用）。
 */
public final class EnumStrings {

    private EnumStrings() {
    }

    public static String nameOrNull(Enum<?> e) {
        return e == null ? null : e.name();
    }
}
