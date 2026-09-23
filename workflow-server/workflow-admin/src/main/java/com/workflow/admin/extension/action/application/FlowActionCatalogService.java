package com.workflow.admin.extension.action.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import com.workflow.contracts.process.action.model.FlowActionDefinitionDescriptor;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.admin.extension.action.api.request.FlowActionDefinitionRequest;
import com.workflow.admin.extension.action.api.response.FlowActionHandlerOption;
import com.workflow.admin.extension.action.infrastructure.persistence.record.FlowActionDefinition;
import com.workflow.admin.extension.action.infrastructure.persistence.record.FlowActionDefinitionEntity;
import com.workflow.admin.extension.action.infrastructure.persistence.mapper.FlowActionDefinitionMapper;
import com.workflow.admin.extension.action.infrastructure.persistence.mapper.FlowActionDefinitionEntityMapper;
import com.workflow.contracts.process.action.spi.FlowActionHandler;
import com.workflow.contracts.process.action.spi.TypedFlowActionHandler;
import com.workflow.admin.extension.action.domain.model.FlowActionVisibilityScope;
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

/**
 * 流程动作定义服务。
 *
 * <p>管理动作处理器目录：扫描容器内所有 {@link FlowActionHandler} Bean，与持久化的动作定义
 * 配置合并，提供前端可见的处理器选项列表；并负责处理器中文名称、可见范围、实体绑定的
 * 增删改与校验。系统管理员可维护目录配置。</p>
 */
@Service
@RequiredArgsConstructor
public class FlowActionCatalogService implements FlowActionCatalogPort {

    private final FlowActionDefinitionMapper definitionMapper;
    private final FlowActionDefinitionEntityMapper definitionEntityMapper;
    private final ApplicationContext applicationContext;
    private final EntityCodeCatalogPort entityCodeCatalogPort;
    private final CurrentUserRoleService currentUserRoleService;

    /**
     * 列出指定流程配置下可见、启用、且对当前实体可见的处理器选项。
     *
     * @param processConfigId 流程配置 ID
     * @return 可选处理器选项列表
     */
    public List<FlowActionHandlerOption> listVisible(String processConfigId) {
        String entityCode = entityCodeCatalogPort.findEntityCodeByProcessDefinitionId(processConfigId);
        return buildOptions(false).stream()
                .filter(option -> Boolean.TRUE.equals(option.getAvailable()))
                .filter(option -> Boolean.TRUE.equals(option.getEnabled()))
                .filter(option -> isVisible(option, entityCode))
                .toList();
    }

    /**
     * 列出全部处理器配置（含未启用的），仅系统管理员可调用。
     *
     * @return 全部处理器选项列表
     * @throws RuntimeException 非系统管理员调用时抛出
     */
    public List<FlowActionHandlerOption> listAllForAdmin() {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以查看流程动作目录");
        return buildOptions(true);
    }

    /**
     * 保存处理器配置：更新中文名称、描述、可见范围与实体绑定。
     *
     * @param beanName 处理器 Bean 名称
     * @param request   配置请求
     * @return 保存后的处理器选项
     * @throws RuntimeException 非管理员、处理器不存在或实体编码无效时抛出
     */
    @Transactional
    @SystemAudit(
            module = AuditModule.ACTION,
            action = AuditAction.CONFIGURE,
            operation = "配置流程动作处理器",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "FLOW_ACTION_HANDLER",
            captureArguments = true,
            captureResult = true)
    public FlowActionHandlerOption save(String beanName, FlowActionDefinitionRequest request) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护流程动作目录");
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
        definition.setEnabled(request.getEnabled() == null || request.getEnabled());
        definition.setUpdatedAt(LocalDateTime.now());

        if (created) {
            definitionMapper.insert(definition);
        } else {
            definitionMapper.updateById(definition);
        }
        replaceVisibleEntities(definition.getId(), entityCodes);
        return toOption(definition, beanName, handler, true);
    }

    /**
     * 校验并返回动作可在指定流程配置下选用的定义。
     *
     * <p>优先按 definitionId 查找，其次按 handlerName 查找；并校验定义已启用、
     * Bean 已注册、对当前实体可见。</p>
     *
     * @param processConfigId 流程配置 ID
     * @param definitionId    动作定义 ID；可为空
     * @param handlerName     处理器 Bean 名称；definitionId 为空时使用
     * @return 可用的动作定义
     * @throws RuntimeException 定义不存在、被禁用、未注册或对实体不可见时抛出
     */
    @Override
    public FlowActionDefinitionDescriptor requireSelectable(
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
        FlowActionHandlerOption option = toOption(
                definition,
                definition.getHandlerName(),
                applicationContext.getBean(definition.getHandlerName(), FlowActionHandler.class),
                true);
        if (!isVisible(option, entityCode)) {
            throw new RuntimeException("该流程动作不允许在当前实体中使用：" + definition.getDisplayName());
        }
        return new FlowActionDefinitionDescriptor(
                definition.getId(),
                definition.getHandlerName(),
                definition.getDisplayName());
    }

    /**
     * 判断处理器是否满足流程发布所需的动作目录前置条件。
     *
     * @param handlerName 处理器名称，后续用于判断是否已配置与可用时匹配或展示
     * @return 已配置与可用条件成立时为 true，否则为 false
     */
    @Override
    public boolean isConfiguredAndAvailable(String handlerName) {
        if (!StringUtils.hasText(handlerName)) {
            return false;
        }
        return definitionMapper.findByHandlerName(handlerName)
                .filter(definition -> Boolean.TRUE.equals(definition.getEnabled()))
                .filter(definition -> applicationContext.containsBean(definition.getHandlerName()))
                .isPresent();
    }

    /**
     * 获取处理器在动作目录中的中文展示名；未配置则返回 handlerName 本身。
     *
     * @param handlerName 处理器 Bean 名称
     * @return 中文展示名
     */
    @Override
    public String displayName(String handlerName) {
        if (!StringUtils.hasText(handlerName)) {
            return null;
        }
        return definitionMapper.findByHandlerName(handlerName)
                .map(FlowActionDefinition::getDisplayName)
                .orElse(handlerName);
    }

    /**
     * 合并容器内全部处理器 Bean 与已持久化定义，组装处理器选项列表。
     *
     * @param includeUnconfigured 是否包含未在目录中配置的处理器
     * @return 处理器选项列表
     */
    private List<FlowActionHandlerOption> buildOptions(boolean includeUnconfigured) {
        Map<String, FlowActionHandler> handlers = applicationContext.getBeansOfType(FlowActionHandler.class);
        Map<String, FlowActionDefinition> definitions = new LinkedHashMap<>();
        for (FlowActionDefinition definition : definitionMapper.findAllActive()) {
            definitions.put(definition.getHandlerName(), definition);
        }

        Set<String> names = new LinkedHashSet<>();
        names.addAll(definitions.keySet());
        names.addAll(handlers.keySet());
        List<FlowActionHandlerOption> result = new ArrayList<>();
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

    /**
     * 将动作定义与处理器 Bean 组装为前端选项 DTO。
     *
     * @param definition 动作定义；为 null 表示未配置
     * @param beanName   处理器 Bean 名称
     * @param handler    处理器实例；为 null 表示 Bean 已失效
     * @param configured 是否已在目录中配置
     * @return 处理器选项 DTO
     */
    private FlowActionHandlerOption toOption(
            FlowActionDefinition definition,
            String beanName,
            FlowActionHandler handler,
            boolean configured) {
        FlowActionHandlerOption option = new FlowActionHandlerOption();
        option.setDefinitionId(definition == null ? null : definition.getId());
        option.setActionCode(definition == null ? beanName : definition.getActionCode());
        option.setBeanName(beanName);
        option.setClassName(handler == null ? null : handler.getClass().getName());
        ExtensionImplementationOrigin implementationOrigin = handler == null
                ? null : handler.implementationOrigin();
        option.setImplementationOrigin(implementationOrigin == null
                ? ExtensionImplementationOrigin.UNKNOWN.name()
                : implementationOrigin.name());
        option.setDisplayName(definition == null ? beanName : definition.getDisplayName());
        option.setDescription(definition == null ? null : definition.getDescription());
        option.setVisibilityScope(definition == null
                ? FlowActionVisibilityScope.ENTITY.name()
                : definition.getVisibilityScope());
        option.setEntityCodes(definition == null
                ? List.of()
                : definitionEntityMapper.findEntityCodes(definition.getId()));
        option.setEnabled(definition != null && Boolean.TRUE.equals(definition.getEnabled()));
        option.setConfigured(configured);
        option.setAvailable(handler != null);
        if (handler != null) {
            option.setTyped(handler instanceof TypedFlowActionHandler<?>);
            if (handler instanceof TypedFlowActionHandler<?> typed) {
                option.setParamType(typed.getParamType().getName());
            }
            option.setSupportedTriggerTimings(handler.supportedTriggerTimings());
            option.setSupportedExecutionModes(handler.supportedExecutionModes());
            option.setRecommendedExecutionMode(handler.recommendedExecutionMode());
            option.setExtraParamSchema(handler.extraParamSchema());
            option.setDynamicExtraParams(handler.dynamicExtraParams());
        }
        return option;
    }

    /**
     * 供统一扩展目录聚合动作定义。权限由聚合入口统一校验。
     *
     * @return 流程动作处理器选项集合，供调用方遍历或展示
     */
    public List<FlowActionHandlerOption> listCatalog() {
        return buildOptions(true);
    }

    /**
     * 判断处理器选项对指定实体是否可见：全局可见直接通过，实体可见需实体编码匹配
     *
     * @param option 选项，供本方法判断是否可见时使用
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 可见条件成立时为 true，否则为 false
     */
    private boolean isVisible(FlowActionHandlerOption option, String entityCode) {
        if (FlowActionVisibilityScope.GLOBAL.name().equals(option.getVisibilityScope())) {
            return true;
        }
        if (!StringUtils.hasText(entityCode)) {
            return false;
        }
        return option.getEntityCodes().stream().anyMatch(code -> code.equalsIgnoreCase(entityCode));
    }

    /**
     * 解析可见范围字符串为枚举，非法值抛出异常
     *
     * @param value 待解析作用域的原始输入，结果供调用方继续使用
     * @return 解析后的作用域结果，供调用方继续处理
     */
    private FlowActionVisibilityScope parseScope(String value) {
        try {
            return FlowActionVisibilityScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new RuntimeException("可见范围只能是 GLOBAL 或 ENTITY");
        }
    }

    /**
     * 归一化实体编码列表：去空白、转小写、去重
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 流程动作目录集合，供调用方遍历或展示
     */
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

    /**
     * 校验实体编码是否全部存在于实体目录中，存在未知编码时抛出异常
     *
     * @param entityCodes 实体编码集合，供本方法校验实体编码集合时使用
     */
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

    /**
     * 重置定义的可见实体绑定：先删除旧关系，再逐条插入新关系
     *
     * @param definitionId 定义ID，后续用于处理替换可见{@code entities}时定位或关联目标
     * @param entityCodes 实体编码集合，供本方法处理替换可见{@code entities}时使用
     */
    private void replaceVisibleEntities(String definitionId, List<String> entityCodes) {
        definitionEntityMapper.deleteByDefinitionId(definitionId);
        for (String entityCode : entityCodes) {
            FlowActionDefinitionEntity relation = new FlowActionDefinitionEntity();
            relation.setActionDefinitionId(definitionId);
            relation.setEntityCode(entityCode);
            relation.setCreatedAt(LocalDateTime.now());
            definitionEntityMapper.insert(relation);
        }
    }

    /**
     * 去除文本首尾空白，并将空白结果转为 null 供后续缺失值判断。
     *
     * @param value 待清理截止空值的原始输入，结果供调用方继续使用
     * @return 清理后的截止空值文本，供调用方比较或展示
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
