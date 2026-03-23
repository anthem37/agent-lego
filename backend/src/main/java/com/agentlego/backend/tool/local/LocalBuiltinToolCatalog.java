package com.agentlego.backend.tool.local;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.tool.application.dto.LocalBuiltinParamMetaDto;
import com.agentlego.backend.tool.application.dto.LocalBuiltinToolMetaDto;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 扫描 Spring 容器内 {@code com.agentlego.backend.*} Bean 上声明的 {@link Tool} / {@link ToolParam}，生成元数据与 Schema；
 * {@link #newInstance(String)} 优先从 {@link ApplicationContext} 取宿主 Bean。
 */
@Component
public class LocalBuiltinToolCatalog {

    private static final String SCAN_PACKAGE_PREFIX = "com.agentlego.backend.";

    private final ApplicationContext applicationContext;
    private final Map<String, Class<?>> byLowerName;
    private final List<LocalBuiltinToolMetaDto> metaList;
    private final String allowedBuiltinNamesCsv;

    private LocalBuiltinToolCatalog(ApplicationContext applicationContext, List<Class<?>> testOnlyHostClasses) {
        this.applicationContext = applicationContext;
        Map<String, Class<?>> map = new LinkedHashMap<>();
        List<LocalBuiltinToolMetaDto> metas = new ArrayList<>();
        if (testOnlyHostClasses != null && !testOnlyHostClasses.isEmpty()) {
            for (Class<?> c : testOnlyHostClasses) {
                collectFromClass(c, map, metas);
            }
        } else if (applicationContext != null) {
            scanApplicationContextBeans(applicationContext, map, metas);
        }
        metas.sort(Comparator.comparing(LocalBuiltinToolMetaDto::getName, String.CASE_INSENSITIVE_ORDER));
        this.byLowerName = Map.copyOf(map);
        this.metaList = List.copyOf(metas);
        this.allowedBuiltinNamesCsv = metas.stream()
                .map(LocalBuiltinToolMetaDto::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Spring 容器用：存在多个构造函数时须显式标注，否则容器会尝试无参构造而失败。
     */
    @Autowired
    public LocalBuiltinToolCatalog(ApplicationContext applicationContext) {
        this(Objects.requireNonNull(applicationContext, "applicationContext"), null);
    }

    /**
     * 单测：在无 Spring 容器时仅扫描指定宿主类上的 {@code @Tool}（与 {@link #newInstance} 的反射实例化配合）。
     */
    public static LocalBuiltinToolCatalog forTests(Class<?>... hostClasses) {
        Objects.requireNonNull(hostClasses, "hostClasses");
        if (hostClasses.length == 0) {
            throw new IllegalArgumentException("hostClasses must not be empty");
        }
        return new LocalBuiltinToolCatalog(null, List.of(hostClasses));
    }

    private static void scanApplicationContextBeans(
            ApplicationContext applicationContext,
            Map<String, Class<?>> map,
            List<LocalBuiltinToolMetaDto> metas
    ) {
        Set<Class<?>> seen = new HashSet<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (BeansException e) {
                continue;
            }
            Class<?> target = AopUtils.getTargetClass(bean);
            if (!isBuiltinToolHostClass(target)) {
                continue;
            }
            if (!seen.add(target)) {
                continue;
            }
            collectFromClass(target, map, metas);
        }
    }

    /**
     * 仅扫描「本仓库」包下的具体类，避免把框架/第三方代理类误扫进来。
     */
    static boolean isBuiltinToolHostClass(Class<?> clazz) {
        if (clazz == null || clazz.isInterface() || clazz.isPrimitive() || clazz.isArray()) {
            return false;
        }
        return clazz.getName().startsWith(SCAN_PACKAGE_PREFIX);
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

    private static String buildOutputDescription(String out, Class<?> conv) {
        if (conv == null) {
            return "返回类型 " + out + "。";
        }
        if (PlainTextToolResultConverter.class.equals(conv)) {
            return String.format("返回类型 %s，经 %s 转为对话中的纯文本块（ToolResultBlock）。", out, conv.getSimpleName());
        }
        return String.format("返回类型 %s；结果转换器：%s。", out, conv.getSimpleName());
    }

    /**
     * 自宿主类向上遍历继承链，收集各层声明的 {@code @Tool}（宿主类用于 {@link #newInstance} 取 Bean）。
     */
    private static void collectFromClass(Class<?> beanClass, Map<String, Class<?>> map, List<LocalBuiltinToolMetaDto> metas) {
        for (Class<?> c = beanClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
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
                    throw new IllegalStateException(
                            "Duplicate @Tool name in local builtins: " + name + " (classes: "
                                    + map.get(key).getName() + " vs " + beanClass.getName() + ")"
                    );
                }
                map.put(key, beanClass);
                String label = name.trim();
                String hint = "";
                String desc = t.description() != null ? t.description() : "";
                List<LocalBuiltinParamMetaDto> inputs = extractInputParameters(m);
                String outType = m.getReturnType().getSimpleName();
                Class<?> converterClass = t.converter();
                String converterSimple = converterClass != null ? converterClass.getSimpleName() : "";
                String outputDesc = buildOutputDescription(outType, converterClass);
                Map<String, Object> outSchema = PlainTextToolResultConverter.class.equals(converterClass)
                        ? LocalBuiltinJsonSchemaBuilder.buildPlainTextOutputSchema(outputDesc)
                        : LocalBuiltinJsonSchemaBuilder.buildOutputObjectSchema(outType, outputDesc, converterSimple);
                metas.add(LocalBuiltinToolMetaDto.builder()
                        .name(name.trim())
                        .description(desc)
                        .label(label)
                        .usageHint(hint)
                        .inputParameters(List.copyOf(inputs))
                        .inputSchema(LocalBuiltinJsonSchemaBuilder.buildInputObjectSchema(inputs))
                        .outputSchema(outSchema)
                        .outputJavaType(outType)
                        .resultConverterClass(converterSimple)
                        .outputDescription(outputDesc)
                        .build());
            }
        }
    }

    public List<LocalBuiltinToolMetaDto> listMeta() {
        return metaList;
    }

    /**
     * 按内置工具名（大小写不敏感）查元数据，供 API 补全 definition、联调预填等。
     */
    public Optional<LocalBuiltinToolMetaDto> findMetaByCanonicalName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        String want = toolName.trim();
        return metaList.stream().filter(m -> m.getName().equalsIgnoreCase(want)).findFirst();
    }

    public Object newInstance(String toolName) {
        Class<?> c = toolName == null || toolName.isBlank()
                ? null
                : byLowerName.get(toolName.trim().toLowerCase(Locale.ROOT));
        if (c == null) {
            throw new ApiException("UNSUPPORTED_LOCAL_TOOL", "不支持的本地工具：" + toolName, HttpStatus.BAD_REQUEST);
        }
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(c);
            } catch (NoSuchBeanDefinitionException ignored) {
            }
        }
        try {
            return c.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate local builtin: " + c.getName(), e);
        }
    }

    public void requireSupportedLocalName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "name 为必填", HttpStatus.BAD_REQUEST);
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (!byLowerName.containsKey(key)) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "LOCAL 工具名称必须是已内置实现之一，当前支持: "
                            + (allowedBuiltinNamesCsv.isBlank() ? "(无)" : allowedBuiltinNamesCsv),
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}
