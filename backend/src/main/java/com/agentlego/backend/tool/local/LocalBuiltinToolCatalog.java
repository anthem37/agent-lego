package com.agentlego.backend.tool.local;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.tool.application.dto.LocalBuiltinParamMetaDto;
import com.agentlego.backend.tool.application.dto.LocalBuiltinToolMetaDto;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 扫描 {@code com.agentlego.backend.tool.local} 下带 {@link Tool} 方法的类，自动维护内置 LOCAL 工具清单与实例构造。
 */
@Component
public class LocalBuiltinToolCatalog {

    private static final String SCAN_BASE_PACKAGE = "com.agentlego.backend.tool.local";

    private final Map<String, Class<?>> byLowerName;
    private final List<LocalBuiltinToolMetaDto> metaList;

    public LocalBuiltinToolCatalog() throws ClassNotFoundException {
        Map<String, Class<?>> map = new LinkedHashMap<>();
        List<LocalBuiltinToolMetaDto> metas = new ArrayList<>();

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        TypeFilter inLocalPackage = (MetadataReader reader, MetadataReaderFactory factory) -> {
            String cn = reader.getClassMetadata().getClassName();
            return cn.startsWith(SCAN_BASE_PACKAGE + ".")
                    && !reader.getClassMetadata().isInterface()
                    && !reader.getClassMetadata().isAbstract();
        };
        scanner.addIncludeFilter(inLocalPackage);

        Set<BeanDefinition> defs = scanner.findCandidateComponents(SCAN_BASE_PACKAGE);
        for (BeanDefinition bd : defs) {
            String className = bd.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> clazz = Class.forName(className);
            if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            collectFromClass(clazz, map, metas);
        }

        metas.sort(Comparator.comparing(LocalBuiltinToolMetaDto::getName, String.CASE_INSENSITIVE_ORDER));
        this.byLowerName = Map.copyOf(map);
        this.metaList = List.copyOf(metas);
    }

    private static List<LocalBuiltinParamMetaDto> extractInputParameters(Method m) {
        List<LocalBuiltinParamMetaDto> list = new ArrayList<>();
        Parameter[] params = m.getParameters();
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            ToolParam tp = p.getAnnotation(ToolParam.class);
            String pname;
            if (tp != null && tp.name() != null && !tp.name().isBlank()) {
                pname = tp.name().trim();
            } else {
                String raw = p.getName();
                pname = (raw != null && !raw.matches("arg\\d+")) ? raw : ("param" + (i + 1));
            }
            boolean required = tp != null && tp.required();
            String pdesc = tp != null && tp.description() != null ? tp.description() : "";
            list.add(LocalBuiltinParamMetaDto.builder()
                    .name(pname)
                    .required(required)
                    .description(pdesc)
                    .type(p.getType().getSimpleName())
                    .build());
        }
        return list;
    }

    private static String buildOutputDescription(String returnSimpleName, Class<?> converterClass) {
        if (PlainTextToolResultConverter.class.equals(converterClass)) {
            return String.format(
                    "返回类型 %s，经 %s 转为对话中的纯文本块（ToolResultBlock）。",
                    returnSimpleName,
                    converterClass.getSimpleName()
            );
        }
        if (converterClass != null) {
            return String.format("返回类型 %s；结果转换器：%s。", returnSimpleName, converterClass.getSimpleName());
        }
        return "返回类型 " + returnSimpleName + "。";
    }

    private void collectFromClass(Class<?> clazz, Map<String, Class<?>> map, List<LocalBuiltinToolMetaDto> metas) {
        LocalBuiltinUiHint ui = clazz.getAnnotation(LocalBuiltinUiHint.class);
        for (Method m : clazz.getDeclaredMethods()) {
            Tool t = m.getAnnotation(Tool.class);
            if (t == null) {
                continue;
            }
            String name = t.name();
            if (name == null || name.isBlank()) {
                continue;
            }
            String key = name.trim().toLowerCase(Locale.ROOT);
            if (map.containsKey(key)) {
                throw new IllegalStateException("Duplicate @Tool name in local builtins: " + name);
            }
            map.put(key, clazz);
            String label = (ui != null && ui.label() != null && !ui.label().isBlank()) ? ui.label() : name.trim();
            String hint = ui != null && ui.hint() != null ? ui.hint() : "";
            String desc = t.description() != null ? t.description() : "";
            List<LocalBuiltinParamMetaDto> inputs = extractInputParameters(m);
            String outType = m.getReturnType().getSimpleName();
            Class<?> converterClass = t.converter();
            String converterSimple = converterClass != null ? converterClass.getSimpleName() : "";
            String outputDesc = buildOutputDescription(outType, converterClass);
            metas.add(LocalBuiltinToolMetaDto.builder()
                    .name(name.trim())
                    .description(desc)
                    .label(label)
                    .usageHint(hint)
                    .inputParameters(List.copyOf(inputs))
                    .outputJavaType(outType)
                    .resultConverterClass(converterSimple)
                    .outputDescription(outputDesc)
                    .build());
        }
    }

    public List<LocalBuiltinToolMetaDto> listMeta() {
        return metaList;
    }

    public Object newInstance(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            throw new ApiException(
                    "UNSUPPORTED_LOCAL_TOOL",
                    "不支持的本地工具：" + toolName,
                    HttpStatus.BAD_REQUEST
            );
        }
        Class<?> c = byLowerName.get(toolName.trim().toLowerCase(Locale.ROOT));
        if (c == null) {
            throw new ApiException(
                    "UNSUPPORTED_LOCAL_TOOL",
                    "不支持的本地工具：" + toolName,
                    HttpStatus.BAD_REQUEST
            );
        }
        try {
            return c.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate local tool host: " + c.getName(), e);
        }
    }

    /**
     * 创建/更新 LOCAL 工具时校验 name 必须为已扫描到的内置名。
     */
    public void requireSupportedLocalName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "name 为必填", HttpStatus.BAD_REQUEST);
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (!byLowerName.containsKey(key)) {
            String allowed = metaList.stream().map(LocalBuiltinToolMetaDto::getName).collect(Collectors.joining(", "));
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "LOCAL 工具名称必须是已内置实现之一，当前支持: " + (allowed.isBlank() ? "(无)" : allowed),
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}
