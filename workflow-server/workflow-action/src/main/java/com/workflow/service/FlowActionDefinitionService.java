package com.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.UserContext;
import com.workflow.contracts.entity.EntityCodeCatalogPort;
import com.workflow.dto.FlowActionDefinitionRequest;
import com.workflow.dto.FlowActionHandlerOptionDTO;
import com.workflow.entity.FlowActionDefinition;
import com.workflow.mapper.FlowActionDefinitionMapper;
import com.workflow.process.action.FlowActionHandler;
import com.workflow.process.action.FlowActionVisibilityScope;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlowActionDefinitionService {

    private final FlowActionDefinitionMapper definitionMapper;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final EntityCodeCatalogPort entityCodeCatalogPort;
    private final CurrentUserRoleService currentUserRoleService;

    public List<FlowActionHandlerOptionDTO> listVisible(String processConfigId) {
        String entityCode = entityCodeCatalogPort.findEntityCodeByProcessDefinitionId(processConfigId);
        return buildOptions(false).stream()
                .filter(option -> Boolean.TRUE.equals(option.getAvailable()))
                .filter(option -> Boolean.TRUE.equals(option.getEnabled()))
                .filter(option -> isVisible(option, entityCode))
                .toList();
    }

    public List<FlowActionHandlerOptionDTO> listAllForAdmin() {
        currentUserRoleService.requireSuperAdmin();
        return buildOptions(true);
    }

    @Transactional
    public FlowActionHandlerOptionDTO save(String beanName, FlowActionDefinitionRequest request) {
        currentUserRoleService.requireSuperAdmin();
        FlowActionHandler handler = applicationContext.getBeansOfType(FlowActionHandler.class).get(beanName);
        if (handler == null) {
            throw new RuntimeException("未找到流程动作处理器：" + beanName);
        }

        FlowActionVisibilityScope visibilityScope = parseScope(request.getVisibilityScope());
        List<String> entityCodes = normalizeEntityCodes(request.getEntityCodes());
        if (visibilityScope == FlowActionVisibilityScope.ENTITY && entityCodes.isEmpty()) {
            throw new RuntimeException("实体可见动作必须至少选择一个实体");
        }
        validateEntityCodes(entityCodes);

        FlowActionDefinition definition = definitionMapper.findByHandlerName(beanName)
                .orElseGet(FlowActionDefinition::new);
        boolean created = !StringUtils.hasText(definition.getId());
        if (created) {
            definition.setActionCode(beanName);
            definition.setHandlerName(beanName);
            definition.setCreatedBy(UserContext.getUsername());
            definition.setCreatedAt(LocalDateTime.now());
            definition.setDeleted(0);
        }
        definition.setDisplayName(request.getDisplayName().trim());
        definition.setDescription(trimToNull(request.getDescription()));
        definition.setVisibilityScope(visibilityScope.name());
        definition.setEntityCodesJson(writeEntityCodes(entityCodes));
        definition.setEnabled(request.getEnabled() == null || request.getEnabled());
        definition.setUpdatedAt(LocalDateTime.now());

        if (created) {
            definitionMapper.insert(definition);
        } else {
            definitionMapper.updateById(definition);
        }
        return toOption(definition, beanName, handler, true);
    }

    public FlowActionDefinition requireSelectable(
            String processConfigId,
            String definitionId,
            String handlerName) {
        FlowActionDefinition definition = null;
        if (StringUtils.hasText(definitionId)) {
            definition = definitionMapper.findActiveById(definitionId).orElse(null);
        }
        if (definition == null && StringUtils.hasText(handlerName)) {
            definition = definitionMapper.findByHandlerName(handlerName).orElse(null);
        }
        if (definition == null) {
            throw new RuntimeException("流程动作处理器尚未加入动作目录，请由超级管理员先配置中文名称与可见范围");
        }
        if (!Boolean.TRUE.equals(definition.getEnabled())) {
            throw new RuntimeException("流程动作处理器已被禁用：" + definition.getDisplayName());
        }
        if (!applicationContext.containsBean(definition.getHandlerName())) {
            throw new RuntimeException("流程动作处理器未注册：" + definition.getHandlerName());
        }

        String entityCode = entityCodeCatalogPort.findEntityCodeByProcessDefinitionId(processConfigId);
        FlowActionHandlerOptionDTO option = toOption(
                definition,
                definition.getHandlerName(),
                applicationContext.getBean(definition.getHandlerName(), FlowActionHandler.class),
                true);
        if (!isVisible(option, entityCode)) {
            throw new RuntimeException("该流程动作不允许在当前实体中使用：" + definition.getDisplayName());
        }
        return definition;
    }

    public String displayName(String handlerName) {
        if (!StringUtils.hasText(handlerName)) {
            return null;
        }
        return definitionMapper.findByHandlerName(handlerName)
                .map(FlowActionDefinition::getDisplayName)
                .orElse(handlerName);
    }

    private List<FlowActionHandlerOptionDTO> buildOptions(boolean includeUnconfigured) {
        Map<String, FlowActionHandler> handlers = applicationContext.getBeansOfType(FlowActionHandler.class);
        Map<String, FlowActionDefinition> definitions = new LinkedHashMap<>();
        for (FlowActionDefinition definition : definitionMapper.findAllActive()) {
            definitions.put(definition.getHandlerName(), definition);
        }

        Set<String> names = new LinkedHashSet<>();
        names.addAll(definitions.keySet());
        names.addAll(handlers.keySet());
        List<FlowActionHandlerOptionDTO> result = new ArrayList<>();
        for (String beanName : names) {
            FlowActionDefinition definition = definitions.get(beanName);
            FlowActionHandler handler = handlers.get(beanName);
            if (!includeUnconfigured && definition == null) {
                continue;
            }
            result.add(toOption(definition, beanName, handler, definition != null));
        }
        return result;
    }

    private FlowActionHandlerOptionDTO toOption(
            FlowActionDefinition definition,
            String beanName,
            FlowActionHandler handler,
            boolean configured) {
        FlowActionHandlerOptionDTO option = new FlowActionHandlerOptionDTO();
        option.setDefinitionId(definition == null ? null : definition.getId());
        option.setActionCode(definition == null ? beanName : definition.getActionCode());
        option.setBeanName(beanName);
        option.setClassName(handler == null ? null : handler.getClass().getName());
        option.setDisplayName(definition == null ? beanName : definition.getDisplayName());
        option.setDescription(definition == null ? null : definition.getDescription());
        option.setVisibilityScope(definition == null
                ? FlowActionVisibilityScope.ENTITY.name()
                : definition.getVisibilityScope());
        option.setEntityCodes(definition == null ? List.of() : readEntityCodes(definition.getEntityCodesJson()));
        option.setEnabled(definition != null && Boolean.TRUE.equals(definition.getEnabled()));
        option.setConfigured(configured);
        option.setAvailable(handler != null);
        if (handler != null) {
            option.setTyped(handler instanceof com.workflow.process.action.TypedFlowActionHandler<?>);
            if (handler instanceof com.workflow.process.action.TypedFlowActionHandler<?> typed) {
                option.setParamType(typed.getParamType().getName());
            }
            option.setSupportedTriggerTimings(handler.supportedTriggerTimings());
            option.setSupportedExecutionModes(handler.supportedExecutionModes());
            option.setRecommendedExecutionMode(handler.recommendedExecutionMode());
        }
        return option;
    }

    private boolean isVisible(FlowActionHandlerOptionDTO option, String entityCode) {
        if (FlowActionVisibilityScope.GLOBAL.name().equals(option.getVisibilityScope())) {
            return true;
        }
        if (!StringUtils.hasText(entityCode)) {
            return false;
        }
        return option.getEntityCodes().stream().anyMatch(code -> code.equalsIgnoreCase(entityCode));
    }

    private FlowActionVisibilityScope parseScope(String value) {
        try {
            return FlowActionVisibilityScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new RuntimeException("可见范围只能是 GLOBAL 或 ENTITY");
        }
    }

    private List<String> normalizeEntityCodes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .toList();
    }

    private void validateEntityCodes(List<String> entityCodes) {
        Set<String> available = entityCodeCatalogPort.findAllEntityCodes().stream()
                .map(code -> code.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        List<String> unknown = entityCodes.stream()
                .filter(code -> !available.contains(code.toLowerCase(Locale.ROOT)))
                .toList();
        if (!unknown.isEmpty()) {
            throw new RuntimeException("存在无效实体编码：" + String.join(", ", unknown));
        }
    }

    private String writeEntityCodes(List<String> entityCodes) {
        try {
            return objectMapper.writeValueAsString(entityCodes);
        } catch (Exception e) {
            throw new RuntimeException("保存动作实体范围失败", e);
        }
    }

    private List<String> readEntityCodes(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
