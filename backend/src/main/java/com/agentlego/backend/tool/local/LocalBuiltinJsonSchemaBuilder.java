package com.agentlego.backend.tool.local;

import com.agentlego.backend.tool.application.dto.LocalBuiltinParamMetaDto;

import java.util.*;

/**
 * 将 {@link LocalBuiltinParamMetaDto} 与出参说明转为 JSON Schema（子集），供 API 与控制台表格展示。
 */
public final class LocalBuiltinJsonSchemaBuilder {

    private static final String JSON_TYPE_OBJECT = "object";
    private static final String JSON_TYPE_STRING = "string";
    private static final String JSON_TYPE_INTEGER = "integer";
    private static final String JSON_TYPE_NUMBER = "number";
    private static final String JSON_TYPE_BOOLEAN = "boolean";
    private static final String JSON_TYPE_ARRAY = "array";

    private static final String KEY_TYPE = "type";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_REQUIRED = "required";
    private static final String KEY_ITEMS = "items";

    private LocalBuiltinJsonSchemaBuilder() {
    }

    /**
     * 由参数列表生成 {@code type:object} 入参 Schema（与 HTTP 工具 parameters 表格兼容）。
     */
    public static Map<String, Object> buildInputObjectSchema(List<LocalBuiltinParamMetaDto> params) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(KEY_TYPE, JSON_TYPE_OBJECT);
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        if (params != null) {
            for (LocalBuiltinParamMetaDto p : params) {
                if (p == null || p.getName() == null || p.getName().isBlank()) {
                    continue;
                }
                String n = p.getName().trim();
                properties.put(n, propertyNodeForJavaSimpleType(p.getType(), p.getDescription()));
                if (p.isRequired()) {
                    required.add(n);
                }
            }
        }
        root.put(KEY_PROPERTIES, properties);
        if (!required.isEmpty()) {
            root.put(KEY_REQUIRED, List.copyOf(required));
        }
        return root;
    }

    /**
     * 纯文本类内置工具（如 {@link PlainTextToolResultConverter}）的出参：与运行时一致，为<strong>单一字符串</strong>，
     * 而非带 {@code summary/javaReturnType} 等字段的 JSON 对象。
     */
    public static Map<String, Object> buildPlainTextOutputSchema(String outputHumanDescription) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(KEY_TYPE, JSON_TYPE_STRING);
        String desc = (outputHumanDescription == null || outputHumanDescription.isBlank())
                ? "工具执行结果：以纯文本形式出现在模型上下文中（ToolResultBlock 文本块）。"
                : outputHumanDescription.trim();
        root.put(KEY_DESCRIPTION, desc);
        return root;
    }

    /**
     * 出参 Schema：非纯文本转换器时，用 object.properties 描述 Java 返回与转换器（与历史 HTTP 表格风格一致）。
     */
    public static Map<String, Object> buildOutputObjectSchema(
            String outputJavaType,
            String outputHumanDescription,
            String resultConverterSimpleName
    ) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(KEY_TYPE, JSON_TYPE_OBJECT);
        String rootDesc = "本地工具执行结果（面向模型/联调说明；运行时经 AgentScope 封装为 ToolResultBlock）。";
        root.put(KEY_DESCRIPTION, rootDesc);

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put(KEY_TYPE, JSON_TYPE_STRING);
        summary.put(KEY_DESCRIPTION, emptyToDash(outputHumanDescription));

        Map<String, Object> javaType = new LinkedHashMap<>();
        javaType.put(KEY_TYPE, JSON_TYPE_STRING);
        javaType.put(KEY_DESCRIPTION, "Java 方法返回类型：" + emptyToDash(outputJavaType));

        Map<String, Object> converter = new LinkedHashMap<>();
        converter.put(KEY_TYPE, JSON_TYPE_STRING);
        String c = resultConverterSimpleName;
        converter.put(
                KEY_DESCRIPTION,
                (c != null && !c.isBlank())
                        ? ("结果转换器：" + c.trim())
                        : "无额外转换器（直接封装返回值）"
        );

        properties.put("summary", summary);
        properties.put("javaReturnType", javaType);
        properties.put("resultConverter", converter);

        root.put(KEY_PROPERTIES, properties);
        return root;
    }

    private static String emptyToDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s.trim();
    }

    private static Map<String, Object> propertyNodeForJavaSimpleType(String javaSimpleName, String description) {
        Map<String, Object> node = new LinkedHashMap<>();
        String t = javaSimpleName == null ? "" : javaSimpleName.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        String desc = description == null || description.isBlank() ? "—" : description.trim();

        if (lower.endsWith("[]") || lower.equals("list") || lower.equals("arraylist")) {
            node.put(KEY_TYPE, JSON_TYPE_ARRAY);
            Map<String, Object> items = new LinkedHashMap<>();
            items.put(KEY_TYPE, JSON_TYPE_STRING);
            items.put(KEY_DESCRIPTION, "数组元素（简化为 string；复杂结构请用 JSON 传参）");
            node.put(KEY_ITEMS, items);
            node.put(KEY_DESCRIPTION, desc);
            return node;
        }
        String jsonType;
        if (lower.equals("int") || lower.equals("integer") || lower.equals("long") || lower.equals("short") || lower.equals("byte")) {
            jsonType = JSON_TYPE_INTEGER;
        } else if (lower.equals("double") || lower.equals("float") || lower.equals("bigdecimal")) {
            jsonType = JSON_TYPE_NUMBER;
        } else if (lower.equals("boolean")) {
            jsonType = JSON_TYPE_BOOLEAN;
        } else {
            jsonType = JSON_TYPE_STRING;
        }
        node.put(KEY_TYPE, jsonType);
        node.put(KEY_DESCRIPTION, desc);
        return node;
    }
}
