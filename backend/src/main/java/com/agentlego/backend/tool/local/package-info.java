/**
 * 平台 LOCAL 工具：在 AgentScope {@link io.agentscope.core.tool.Tool} /
 * {@link io.agentscope.core.tool.ToolParam} 契约上扩展；{@link com.agentlego.backend.tool.local.LocalBuiltinToolCatalog}
 * 扫描 Spring 容器中 {@code com.agentlego.backend} 包下 Bean 上带 {@link io.agentscope.core.tool.Tool} 的方法生成元数据与 JSON Schema；新增内置在任意宿主 Bean 上增加 {@code @Tool} 方法即可。
 */
package com.agentlego.backend.tool.local;
