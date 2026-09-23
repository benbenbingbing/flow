package com.workflow.admin.extension.catalog.application;

import com.workflow.admin.extension.action.api.response.FlowActionHandlerOption;
import com.workflow.admin.extension.action.application.FlowActionCatalogService;
import com.workflow.admin.extension.catalog.api.response.ExtensionCatalogItem;
import com.workflow.admin.extension.person.api.response.PersonResolverOption;
import com.workflow.admin.extension.person.application.PersonResolverCatalogService;
import com.workflow.contracts.entity.ui.port.UiExtensionCatalogPort;
import com.workflow.contracts.entity.ui.spi.UiActionCommandPlanProvider;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.contracts.entity.ui.model.UiExtensionCatalogItem;
import com.workflow.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 聚合后端动作、人员解析器和 UI 扩展的统一管理目录。
 */
@Service
@RequiredArgsConstructor
public class ExtensionCatalogService {

    private final FlowActionCatalogService flowActionCatalogService;
    private final PersonResolverCatalogService personResolverCatalogService;
    private final UiExtensionCatalogPort uiExtensionCatalogPort;
    /** 延迟解析 Provider，避免扩展目录参与业务 Provider 的初始化依赖图。 */
    private final ObjectProvider<UiDataSourceProvider> dataSourceProviders;
    /** WRITE 接口使用独立的受控命令计划 Provider。 */
    private final ObjectProvider<UiActionCommandPlanProvider>
            actionCommandPlanProviders;

    /**
     * 处理{@code manage}，并将结果传给后续步骤。
     *
     * @param capabilityType 能力类型标识，决定后续{@code manage}采用的处理分支
     * @param keyword 关键字，作为 {@code lower} 的输入影响后续处理
     * @param status 状态标识，决定后续{@code manage}采用的处理分支
     * @param implementationOrigin 实现来源，作为 {@code normalize} 的输入影响后续处理
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code manage}结果，供调用方继续处理
     */
    public PageResult<ExtensionCatalogItem> manage(
            String capabilityType,
            String keyword,
            String status,
            String implementationOrigin,
            Integer pageNum,
            Integer pageSize) {
        int currentPage = pageNum == null ? 1 : Math.max(1, pageNum);
        int currentSize = pageSize == null
                ? 20
                : Math.max(1, Math.min(pageSize, 200));
        String normalizedType = normalize(capabilityType);
        String normalizedKeyword = lower(keyword);
        String normalizedStatus = normalize(status);
        String normalizedOrigin = normalize(implementationOrigin);

        List<ExtensionCatalogItem> matched = allItems().stream()
                .filter(item -> !StringUtils.hasText(normalizedType)
                        || normalizedType.equals(item.getCapabilityType()))
                .filter(item -> !StringUtils.hasText(normalizedStatus)
                        || normalizedStatus.equals(item.getStatus()))
                .filter(item -> !StringUtils.hasText(normalizedOrigin)
                        || normalizedOrigin.equals(
                                item.getImplementationOrigin()))
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .sorted(Comparator
                        .comparing(ExtensionCatalogItem::getCapabilityType)
                        .thenComparing(
                                item -> safe(item.getDisplayName()),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                item -> safe(item.getKey()),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();

        int from = Math.min((currentPage - 1) * currentSize, matched.size());
        int to = Math.min(from + currentSize, matched.size());
        return new PageResult<>(
                matched.subList(from, to),
                matched.size(),
                currentPage,
                currentSize);
    }

    /**
     * 整理选项数据，供调用方遍历或继续处理。
     *
     * @param capabilityType 能力类型标识，决定后续选项采用的处理分支
     * @param keyword 关键字，作为 {@code lower} 的输入影响后续处理
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param processConfigId 流程配置ID，后续用于处理选项时定位或关联目标
     * @param usage 使用场景，作为 {@code listVisible} 的输入影响后续处理
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 扩展目录条目集合，供调用方遍历或展示
     */
    public List<ExtensionCatalogItem> options(
            String capabilityType,
            String keyword,
            Integer limit,
            String processConfigId,
            String usage,
            String entityCode) {
        String normalizedType = normalize(capabilityType);
        String normalizedKeyword = lower(keyword);
        int max = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        List<ExtensionCatalogItem> candidates;
        if ("FLOW_ACTION".equals(normalizedType)
                && StringUtils.hasText(processConfigId)) {
            candidates = flowActionCatalogService.listVisible(processConfigId)
                    .stream()
                    .map(this::actionItem)
                    .toList();
        } else if ("PERSON_RESOLVER".equals(normalizedType)
                && StringUtils.hasText(usage)) {
            candidates = personResolverCatalogService
                    .listVisible(usage, keyword, max)
                    .stream()
                    .map(this::personItem)
                    .toList();
        } else {
            candidates = allItems();
        }
        return candidates.stream()
                .filter(item -> !StringUtils.hasText(normalizedType)
                        || normalizedType.equals(item.getCapabilityType()))
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .filter(item -> !Boolean.FALSE.equals(item.getAvailable()))
                .filter(item -> matchesEntityScope(item, entityCode))
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .sorted(Comparator
                        .comparing(
                                (ExtensionCatalogItem item) ->
                                        safe(item.getDisplayName()),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                item -> safe(item.getKey()),
                                String.CASE_INSENSITIVE_ORDER))
                .limit(max)
                .toList();
    }

    /**
     * 整理全部条目数据，供调用方遍历或继续处理。
     *
     * @return 扩展目录条目集合，供调用方遍历或展示
     */
    public List<ExtensionCatalogItem> allItems() {
        List<ExtensionCatalogItem> items = new ArrayList<>();
        flowActionCatalogService.listCatalog().stream()
                .map(this::actionItem)
                .forEach(items::add);
        personResolverCatalogService.listCatalog().stream()
                .map(this::personItem)
                .forEach(items::add);
        uiExtensionCatalogPort.listCatalogItems().stream()
                .map(this::uiItem)
                .forEach(items::add);
        return items;
    }

    /**
     * 处理动作条目，并将结果传给后续步骤。
     *
     * @param source 待处理动作条目的原始输入，结果供调用方继续使用
     * @return 处理后的动作条目结果，供调用方继续处理
     */
    private ExtensionCatalogItem actionItem(
            FlowActionHandlerOption source) {
        ExtensionCatalogItem item = new ExtensionCatalogItem();
        item.setId(source.getDefinitionId());
        item.setCapabilityType("FLOW_ACTION");
        item.setKey(source.getActionCode());
        item.setDisplayName(source.getDisplayName());
        item.setDescription(source.getDescription());
        item.setImplementationOrigin(source.getImplementationOrigin());
        item.setImplementationVersion(1);
        item.setContractVersion(1);
        item.setSourceType("SPRING");
        item.setSourceName(source.getBeanName());
        item.setImplementationClass(source.getClassName());
        item.setStatus(status(
                source.getConfigured(),
                source.getAvailable(),
                source.getEnabled()));
        item.setConfigured(source.getConfigured());
        item.setAvailable(source.getAvailable());
        item.setEnabled(source.getEnabled());
        item.setVisibilityScope(source.getVisibilityScope());
        item.setEntityCodes(source.getEntityCodes());
        item.setSupportedTriggerTimings(
                emptySet(source.getSupportedTriggerTimings()));
        item.setSupportedExecutionModes(
                emptySet(source.getSupportedExecutionModes()));
        item.setRecommendedExecutionMode(
                source.getRecommendedExecutionMode());
        item.setParameterType(source.getParamType());
        item.setConfigSchema(valueOrEmpty(source.getExtraParamSchema()));
        item.setExtraParamSchema(valueOrEmpty(source.getExtraParamSchema()));
        item.setDynamicExtraParams(
                Boolean.TRUE.equals(source.getDynamicExtraParams()));
        return item;
    }

    /**
     * 处理人员条目，并将结果传给后续步骤。
     *
     * @param source 待处理人员条目的原始输入，结果供调用方继续使用
     * @return 处理后的人员条目结果，供调用方继续处理
     */
    private ExtensionCatalogItem personItem(
            PersonResolverOption source) {
        ExtensionCatalogItem item = new ExtensionCatalogItem();
        item.setId(source.getDefinitionId());
        item.setCapabilityType("PERSON_RESOLVER");
        item.setKey(source.getResolverCode());
        item.setDisplayName(source.getDisplayName());
        item.setDescription(source.getDescription());
        item.setImplementationOrigin(source.getImplementationOrigin());
        item.setImplementationVersion(source.getImplementationVersion());
        item.setContractVersion(source.getContractVersion());
        item.setSourceType("SPRING");
        item.setSourceName(source.getBeanName());
        item.setImplementationClass(source.getClassName());
        item.setStatus(status(
                source.getConfigured(),
                source.getAvailable(),
                source.getEnabled()));
        item.setConfigured(source.getConfigured());
        item.setAvailable(source.getAvailable());
        item.setEnabled(source.getEnabled());
        item.setVisibilityScope("GLOBAL");
        item.setEntityCodes(List.of());
        item.setSupportedUsages(emptySet(source.getSupportedUsages()));
        item.setConfigSchema(valueOrEmpty(source.getExtraParamSchema()));
        item.setExtraParamSchema(valueOrEmpty(source.getExtraParamSchema()));
        item.setDynamicExtraParams(
                Boolean.TRUE.equals(source.getDynamicExtraParams()));
        item.setRevision(source.getRevision());
        return item;
    }

    /**
     * 处理界面条目，并将结果传给后续步骤。
     *
     * @param source 待处理界面条目的原始输入，结果供调用方继续使用
     * @return 处理后的界面条目结果，供调用方继续处理
     */
    private ExtensionCatalogItem uiItem(UiExtensionCatalogItem source) {
        ExtensionCatalogItem item = new ExtensionCatalogItem();
        item.setId(source.id());
        boolean interfaceExtension = "INTERFACE".equals(
                normalize(source.extensionType()));
        item.setCapabilityType(interfaceExtension
                ? "INTERFACE"
                : "UI_" + normalize(source.extensionType()));
        item.setImplementationOrigin(uiImplementationOrigin(
                source, interfaceExtension));
        item.setKey(source.extensionKey());
        item.setDisplayName(source.displayName());
        item.setImplementationVersion(source.version());
        item.setSnapshotVersion(source.snapshotVersion());
        item.setContractVersion(1);
        item.setSourceType(interfaceExtension
                ? normalize(source.implementationType())
                : "FRONTEND_BUNDLE");
        item.setSourceName(interfaceExtension
                ? source.providerCode() : null);
        item.setStatus(normalize(source.status()));
        item.setConfigured(true);
        item.setAvailable(true);
        item.setEnabled("ACTIVE".equals(normalize(source.status())));
        item.setVisibilityScope(interfaceExtension
                ? normalize(source.scopeType())
                : StringUtils.hasText(source.visibilityScope())
                ? normalize(source.visibilityScope())
                : "GLOBAL");
        item.setEntityCodes(source.entityCodes() == null
                ? List.of()
                : List.copyOf(source.entityCodes()));
        item.setSupportedModes(emptySet(source.supportedModes()));
        item.setSupportedNodeTypes(emptySet(source.supportedNodeTypes()));
        item.setSupportedBindings(emptySet(source.supportedBindings()));
        item.setConfigSchema(
                source.configSchema() == null ? Map.of() : source.configSchema());
        Map<String, Object> capabilities = valueOrEmpty(source.capabilities());
        item.setCapabilities(capabilities);
        item.setExtraParamSchema(Map.of());
        item.setDynamicExtraParams(Boolean.TRUE.equals(
                capabilities.get("dynamicExtraParams")));
        item.setRevision(source.revision());
        item.setImplementationType(source.implementationType());
        item.setProviderCode(source.providerCode());
        item.setScopeType(source.scopeType());
        item.setScopeId(source.scopeId());
        item.setInterfaceKind(source.interfaceKind());
        item.setInterfaceContextType(source.interfaceContextType());
        item.setImplementationConfig(valueOrEmpty(
                source.implementationConfig()));
        item.setExecutionPolicy(valueOrEmpty(
                source.executionPolicy()));
        item.setInputSchema(valueOrEmpty(source.inputSchema()));
        item.setOutputSchema(valueOrEmpty(source.outputSchema()));
        return item;
    }

    /**
     * UI 组件目录当前只收项目注册实现；接口扩展则按执行机制与 Provider 声明判定。
     *
     * @param source 待处理界面实现来源的原始输入，结果供调用方继续使用
     * @param interfaceExtension 接口扩展，供本方法处理界面实现来源时使用
     * @return 处理后的界面实现来源文本，供调用方比较或展示
     */
    private String uiImplementationOrigin(
            UiExtensionCatalogItem source,
            boolean interfaceExtension) {
        if (!interfaceExtension) {
            return ExtensionImplementationOrigin.CUSTOM.name();
        }
        return switch (normalize(source.implementationType())) {
            case "DICTIONARY", "STATIC_OPTIONS", "RUNTIME_CONTEXT",
                    "STRUCTURED_COMPUTE" ->
                    ExtensionImplementationOrigin.PLATFORM.name();
            case "REGISTERED_PROVIDER" -> registeredProviderOrigin(source);
            default -> ExtensionImplementationOrigin.UNKNOWN.name();
        };
    }

    /**
     * 根据接口读写类型查找对应 Provider；未加载或归属声明冲突时返回 UNKNOWN。
     *
     * @param source 待处理{@code registered}提供者来源的原始输入，结果供调用方继续使用
     * @return 处理后的{@code registered}提供者来源文本，供调用方比较或展示
     */
    private String registeredProviderOrigin(UiExtensionCatalogItem source) {
        if (!StringUtils.hasText(source.providerCode())) {
            return ExtensionImplementationOrigin.UNKNOWN.name();
        }
        Set<ExtensionImplementationOrigin> origins;
        if ("WRITE".equals(normalize(source.interfaceKind()))) {
            origins = actionCommandPlanProviders.stream()
                    .filter(provider -> sameProviderCode(
                            provider.getCode(), source.providerCode()))
                    .map(UiActionCommandPlanProvider::implementationOrigin)
                    .collect(java.util.stream.Collectors.toSet());
        } else {
            origins = dataSourceProviders.stream()
                    .filter(provider -> sameProviderCode(
                            provider.getCode(), source.providerCode()))
                    .map(UiDataSourceProvider::implementationOrigin)
                    .collect(java.util.stream.Collectors.toSet());
        }
        return origins.size() == 1 && !origins.contains(null)
                ? origins.iterator().next().name()
                : ExtensionImplementationOrigin.UNKNOWN.name();
    }

    /**
     * 判断相同提供者编码条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，供本方法处理相同提供者编码时使用
     * @param right 右侧，供本方法处理相同提供者编码时使用
     * @return 相同提供者编码条件成立时为 true，否则为 false
     */
    private boolean sameProviderCode(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.trim().equalsIgnoreCase(right.trim());
    }

    /**
     * 判断是否匹配实体作用域；判断结果决定调用方的后续分支。
     *
     * @param item 条目，供本方法判断是否匹配实体作用域时使用
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 实体作用域条件成立时为 true，否则为 false
     */
    private boolean matchesEntityScope(
            ExtensionCatalogItem item,
            String entityCode) {
        if (!String.valueOf(item.getCapabilityType()).startsWith("UI_")
                || !"ENTITY".equals(normalize(
                        item.getVisibilityScope()))) {
            return true;
        }
        if (!StringUtils.hasText(entityCode)) {
            return false;
        }
        return item.getEntityCodes() != null
                && item.getEntityCodes().stream().anyMatch(configured ->
                        configured.equalsIgnoreCase(entityCode.trim()));
    }

    /**
     * 生成状态文本，供后续匹配或展示。
     *
     * @param configured 已配置，供本方法处理状态时使用
     * @param available 可用，供本方法处理状态时使用
     * @param enabled 启用，作为 {@code Boolean.TRUE.equals} 的输入影响后续处理
     * @return 处理后的状态文本，供调用方比较或展示
     */
    private String status(
            Boolean configured,
            Boolean available,
            Boolean enabled) {
        if (!Boolean.TRUE.equals(configured)) {
            return "DISCOVERED";
        }
        if (!Boolean.TRUE.equals(available)) {
            return "MISSING";
        }
        return Boolean.TRUE.equals(enabled) ? "ACTIVE" : "DISABLED";
    }

    /**
     * 判断是否匹配关键字；判断结果决定调用方的后续分支。
     *
     * @param item 条目，作为 {@code lower} 的输入影响后续处理
     * @param keyword 关键字，供本方法判断是否匹配关键字时使用
     * @return 关键字条件成立时为 true，否则为 false
     */
    private boolean matchesKeyword(
            ExtensionCatalogItem item,
            String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return lower(item.getKey()).contains(keyword)
                || lower(item.getDisplayName()).contains(keyword)
                || lower(item.getDescription()).contains(keyword)
                || lower(item.getSourceName()).contains(keyword)
                || lower(item.getImplementationClass()).contains(keyword);
    }

    /**
     * 整理空设置数据，供调用方遍历或继续处理。
     *
     * @param value 待处理空设置的原始输入，结果供调用方继续使用
     * @return 扩展目录集合，供调用方遍历或展示
     */
    private <T> Set<T> emptySet(Set<T> value) {
        return value == null ? Set.of() : value;
    }

    /**
     * 整理值或空数据，供调用方遍历或继续处理。
     *
     * @param value 待处理值或空的原始输入，结果供调用方继续使用
     * @return 值或空键值结果，供调用方继续处理
     */
    private Map<String, Object> valueOrEmpty(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化扩展目录的原始输入，结果供调用方继续使用
     * @return 规范化后的扩展目录文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    /**
     * 生成{@code lower}文本，供后续匹配或展示。
     *
     * @param value 待处理{@code lower}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code lower}文本，供调用方比较或展示
     */
    private String lower(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT)
                : "";
    }

    /**
     * 生成安全文本，供后续匹配或展示。
     *
     * @param value 待处理安全的原始输入，结果供调用方继续使用
     * @return 处理后的安全文本，供调用方比较或展示
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}
