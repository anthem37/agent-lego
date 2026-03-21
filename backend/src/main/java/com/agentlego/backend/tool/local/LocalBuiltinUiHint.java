package com.agentlego.backend.tool.local;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可选：为内置 LOCAL 工具提供前端展示用中文文案（名称/联调说明）。
 * <p>
 * 扫描 {@link LocalBuiltinToolCatalog} 时会读取；未标注时 {@code label} 回退为工具 {@code name}。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LocalBuiltinUiHint {

    /**
     * 下拉展示标签，如 {@code echo — 回显文本}。
     */
    String label() default "";

    /**
     * 表单下方提示，如联调参数说明。
     */
    String hint() default "";
}
