package com.workflow.extension.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.PageResult;
import com.workflow.dto.FlowActionHandlerOptionDTO;
import com.workflow.dto.PersonResolverOptionDTO;
import com.workflow.entity.UiExtensionDefinition;
import com.workflow.extension.dto.ExtensionCatalogItemDTO;
import com.workflow.service.FlowActionDefinitionService;
import com.workflow.service.PersonResolverDefinitionService;
import com.workflow.service.UiExtensionDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 聚合各业务模块扩展目录的只读服务。
 */
@Service
@RequiredArgsConstructor
public class ExtensionCatalogService {

    private final FlowActionDefinitionService flowActionService;
    private final PersonResolverDefinitionService personResolverService;
    private final UiExtensionDefinitionService uiExtensionService;
    private final ObjectMapper objectMapper;

    public PageResult<ExtensionCatalogItemDTO> manage(
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

        List<ExtensionCatalogItemDTO> matched = allItems().stream()
                .filter(item -> !StringUtils.hasText(normalizedType)
                        || normalizedType.equals(item.getCapabilityType()))
                .filter(item -> !StringUtils.hasText(normalizedStatus)
                        || normalizedStatus.equals(item.getStatus()))
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .sorted(Comparator
                        .comparing(ExtensionCatalogItemDTO::getCapabilityType)
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

    public List<ExtensionCatalogItemDTO> options(
            String capabilityType,
            String keyword,
            Integer limit,
            String processConfigId,
            String usage) {
        String normalizedType = normalize(capabilityType);
        String normalizedKeyword = lower(keyword);
        int max = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        List<ExtensionCatalogItemDTO> candidates;
        if ("FLOW_ACTION".equals(normalizedType)
                && StringUtils.hasText(processConfigId)) {
            candidates = flowActionService.listVisible(processConfigId)
                    .stream()
                    .map(this::actionItem)
                    .toList();
        } else if ("PERSON_RESOLVER".equals(normalizedType)
                && StringUtils.hasText(usage)) {
            candidates = personResolverService
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
                                (ExtensionCatalogItemDTO item) ->
                                        safe(item.getDisplayName()),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                item -> safe(item.getKey()),
                                String.CASE_INSENSITIVE_ORDER))
                .limit(max)
                .toList();
    }

    public List<ExtensionCatalogItemDTO> allItems() {
        List<ExtensionCatalogItemDTO> items = new ArrayList<>();
        flowActionService.listCatalog().stream()
                .map(this::actionItem)
                .forEach(items::add);
        personResolverService.listCatalog().stream()
                .map(this::personItem)
                .forEach(items::add);
        uiExtensionService.list(null, null, null).stream()
                .map(this::uiItem)
                .forEach(items::add);
        return items;
    }

    private ExtensionCatalogItemDTO actionItem(
            FlowActionHandlerOptionDTO source) {
        ExtensionCatalogItemDTO item = new ExtensionCatalogItemDTO();
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
        item.setConfigSchema(
                source.getExtraParamSchema() == null
                        ? Map.of()
                        : source.getExtraParamSchema());
        item.setExtraParamSchema(
                source.getExtraParamSchema() == null
                        ? Map.of()
                        : source.getExtraParamSchema());
        item.setDynamicExtraParams(
                Boolean.TRUE.equals(source.getDynamicExtraParams()));
        return item;
    }

    private ExtensionCatalogItemDTO personItem(
            PersonResolverOptionDTO source) {
        ExtensionCatalogItemDTO item = new ExtensionCatalogItemDTO();
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
        item.setConfigSchema(
                source.getExtraParamSchema() == null
                        ? Map.of()
                        : source.getExtraParamSchema());
        item.setExtraParamSchema(
                source.getExtraParamSchema() == null
                        ? Map.of()
                        : source.getExtraParamSchema());
        item.setDynamicExtraParams(
                Boolean.TRUE.equals(source.getDynamicExtraParams()));
        item.setRevision(source.getRevision());
        return item;
    }

    private ExtensionCatalogItemDTO uiItem(UiExtensionDefinition source) {
        ExtensionCatalogItemDTO item = new ExtensionCatalogItemDTO();
        item.setId(source.getId());
        item.setCapabilityType("UI_" + normalize(source.getExtensionType()));
        item.setKey(source.getExtensionKey());
        item.setDisplayName(source.getDisplayName());
        item.setImplementationVersion(source.getVersion());
        item.setSnapshotVersion(source.getSnapshotVersion());
        item.setContractVersion(1);
        item.setSourceType("FRONTEND_BUNDLE");
        item.setStatus(normalize(source.getStatus()));
        item.setConfigured(true);
        item.setEnabled("ACTIVE".equals(normalize(source.getStatus())));
        item.setVisibilityScope("GLOBAL");
        item.setEntityCodes(List.of());
        item.setSupportedModes(readSet(
                source.getSupportedModesDocument()));
        item.setSupportedNodeTypes(readSet(
                source.getSupportedNodeTypesDocument()));
        item.setSupportedBindings(readSet(
                source.getSupportedBindingsDocument()));
        item.setConfigSchema(readDocument(
                source.getConfigSchemaDocument()));
        Map<String, Object> capabilities =
                readMap(source.getCapabilitiesDocument());
        item.setCapabilities(capabilities);
        item.setExtraParamSchema(Map.of());
        item.setDynamicExtraParams(Boolean.TRUE.equals(
                capabilities.get("dynamicExtraParams")));
        item.setRevision(source.getRevision());
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
            ExtensionCatalogItemDTO item,
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

    private Set<String> readSet(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        try {
            return new LinkedHashSet<>(objectMapper.readValue(
                    value, new TypeReference<List<String>>() {}));
        } catch (Exception exception) {
            return Set.of();
        }
    }

    private Map<String, Object> readMap(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Object readDocument(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Set<String> emptySet(Set<String> value) {
        return value == null ? Set.of() : value;
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
