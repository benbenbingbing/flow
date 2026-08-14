package com.workflow.entity.mutationpolicy.application;

import com.workflow.contracts.entity.mutation.EntityChangeTargetResolver;
import com.workflow.contracts.entity.mutation.EntityMutationStepProvider;
import com.workflow.core.result.PageResult;
import com.workflow.entity.ui.application.UiDataSourceService;
import com.workflow.entity.version.application.EntityMutationStepExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only catalog of implementations available to mutation policies. */
@Service
@RequiredArgsConstructor
public class EntityMutationCatalogService {

    private final EntityMutationStepExecutor stepExecutor;
    private final UiDataSourceService dataSourceService;
    private final List<EntityMutationStepProvider> stepProviders;
    private final List<EntityChangeTargetResolver> targetResolvers;

    public Map<String, Object> catalog() {
        Map<String, Object> result = new LinkedHashMap<>(
                stepExecutor.catalog());
        result.put("optionTypes", List.of(
                "MANAGED_INTERFACE",
                "JAVA_PROVIDER",
                "TARGET_RESOLVER"));
        return result;
    }

    public PageResult<Map<String, Object>> options(
            String type,
            String keyword,
            long pageNum,
            long pageSize) {
        String normalizedType = type == null
                ? "" : type.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> values = switch (normalizedType) {
            case "MANAGED_INTERFACE" -> managedInterfaces();
            case "JAVA_PROVIDER" -> javaProviders();
            case "TARGET_RESOLVER" -> targetResolverOptions();
            default -> throw new IllegalArgumentException(
                    "不支持的实体变更实现类型: " + type);
        };
        String normalizedKeyword = StringUtils.hasText(keyword)
                ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        List<Map<String, Object>> filtered = normalizedKeyword == null
                ? values
                : values.stream()
                        .filter(item -> matches(item, normalizedKeyword))
                        .toList();
        long safePage = Math.max(1, pageNum);
        long safeSize = Math.max(1, Math.min(50, pageSize));
        int from = (int) Math.min(
                filtered.size(), (safePage - 1) * safeSize);
        int to = (int) Math.min(filtered.size(), from + safeSize);
        return new PageResult<>(
                new ArrayList<>(filtered.subList(from, to)),
                filtered.size(),
                safePage,
                safeSize);
    }

    private List<Map<String, Object>> managedInterfaces() {
        return dataSourceService.list(null, null, null).stream()
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .map(item -> {
                    Map<String, Object> value = option(
                            item.getId(),
                            item.getSourceCode(),
                            item.getSourceName(),
                            item.getSourceType());
                    value.put("scopeType", item.getScopeType());
                    value.put("capability",
                            item.getOperationsDocument() == null
                                    ? "[]" : item.getOperationsDocument());
                    return value;
                })
                .sorted(optionComparator())
                .toList();
    }

    private List<Map<String, Object>> javaProviders() {
        return stepProviders.stream()
                .map(provider -> {
                    Map<String, Object> value = option(
                            provider.getCode(),
                            provider.getCode(),
                            provider.getDisplayName(),
                            "变更 Provider");
                    value.put("capability", provider.supportedPhases());
                    value.put("schema", provider.configurationSchema());
                    return value;
                })
                .sorted(optionComparator())
                .toList();
    }

    private List<Map<String, Object>> targetResolverOptions() {
        return targetResolvers.stream()
                .map(resolver -> {
                    Map<String, Object> value = option(
                            resolver.getCode(),
                            resolver.getCode(),
                            resolver.getDisplayName(),
                            "目标解析器");
                    value.put("schema", resolver.configurationSchema());
                    return value;
                })
                .sorted(optionComparator())
                .toList();
    }

    private Map<String, Object> option(
            String value,
            String code,
            String name,
            String category) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("code", code);
        result.put("name", name);
        result.put("category", StringUtils.hasText(category)
                ? category : "未分类");
        return result;
    }

    private Comparator<Map<String, Object>> optionComparator() {
        return Comparator.comparing(
                item -> String.valueOf(item.getOrDefault("code", "")),
                String.CASE_INSENSITIVE_ORDER);
    }

    private boolean matches(
            Map<String, Object> item,
            String keyword) {
        return List.of("name", "code", "category").stream()
                .map(item::get)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(keyword));
    }
}
