package com.workflow.admin.extension.catalog.application;

import com.workflow.admin.extension.action.api.response.FlowActionHandlerOption;
import com.workflow.admin.extension.action.application.FlowActionCatalogService;
import com.workflow.admin.extension.catalog.api.response.ExtensionCatalogItem;
import com.workflow.admin.extension.person.api.response.PersonResolverOption;
import com.workflow.admin.extension.person.application.PersonResolverCatalogService;
import com.workflow.contracts.ui.catalog.UiExtensionCatalogItem;
import com.workflow.contracts.ui.catalog.UiExtensionCatalogPort;
import com.workflow.core.result.PageResult;
import lombok.RequiredArgsConstructor;
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

    public PageResult<ExtensionCatalogItem> manage(
            String capabilityType,
            String keyword,
            String status,
            Integer pageNum,
            Integer pageSize) {
        int currentPage = pageNum == null ? 1 : Math.max(1, pageNum);
        int currentSize = pageSize == null
                ? 20
                : Math.max(1, Math.min(pageSize, 200));
        String normalizedType = normalize(capabilityType);
        String normalizedKeyword = lower(keyword);
        String normalizedStatus = normalize(status);

        List<ExtensionCatalogItem> matched = allItems().stream()
                .filter(item -> !StringUtils.hasText(normalizedType)
                        || normalizedType.equals(item.getCapabilityType()))
                .filter(item -> !StringUtils.hasText(normalizedStatus)
                        || normalizedStatus.equals(item.getStatus()))
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

    public List<ExtensionCatalogItem> options(
            String capabilityType,
            String keyword,
            Integer limit,
            String processConfigId,
            String usage) {
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

    private ExtensionCatalogItem actionItem(
            FlowActionHandlerOption source) {
        ExtensionCatalogItem item = new ExtensionCatalogItem();
        item.setId(source.getDefinitionId());
        item.setCapabilityType("FLOW_ACTION");
        item.setKey(source.getActionCode());
        item.setDisplayName(source.getDisplayName());
        item.setDescription(source.getDescription());
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

    private ExtensionCatalogItem personItem(
            PersonResolverOption source) {
        ExtensionCatalogItem item = new ExtensionCatalogItem();
        item.setId(source.getDefinitionId());
        item.setCapabilityType("PERSON_RESOLVER");
        item.setKey(source.getResolverCode());
        item.setDisplayName(source.getDisplayName());
        item.setDescription(source.getDescription());
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

    private ExtensionCatalogItem uiItem(UiExtensionCatalogItem source) {
        ExtensionCatalogItem item = new ExtensionCatalogItem();
        item.setId(source.id());
        item.setCapabilityType("UI_" + normalize(source.extensionType()));
        item.setKey(source.extensionKey());
        item.setDisplayName(source.displayName());
        item.setImplementationVersion(source.version());
        item.setSnapshotVersion(source.snapshotVersion());
        item.setContractVersion(1);
        item.setSourceType("FRONTEND_BUNDLE");
        item.setStatus(normalize(source.status()));
        item.setConfigured(true);
        item.setAvailable(true);
        item.setEnabled("ACTIVE".equals(normalize(source.status())));
        item.setVisibilityScope("GLOBAL");
        item.setEntityCodes(List.of());
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
        return item;
    }

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

    private <T> Set<T> emptySet(Set<T> value) {
        return value == null ? Set.of() : value;
    }

    private Map<String, Object> valueOrEmpty(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    private String lower(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT)
                : "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
