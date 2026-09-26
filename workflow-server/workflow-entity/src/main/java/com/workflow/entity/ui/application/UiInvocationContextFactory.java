package com.workflow.entity.ui.application;

import com.workflow.entity.ui.application.model.UiDataSourceExecutionAuthorization;

import com.workflow.contracts.entity.ui.context.CommonInvocationContext;
import com.workflow.contracts.entity.ui.model.EntityDescriptor;
import com.workflow.contracts.entity.ui.context.EntityInvocationContext;
import com.workflow.contracts.entity.ui.context.FormInvocationContext;
import com.workflow.contracts.entity.ui.context.ListInvocationContext;
import com.workflow.contracts.entity.ui.context.UiInvocationContext;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 根据服务端校验后的授权结果构造 Provider 强类型上下文。
 */
@Component
@RequiredArgsConstructor
public class UiInvocationContextFactory {

    /** 实体定义查询入口，用于解析可信实体身份。 */
    private final EntityDefinitionMapper definitionMapper;
    /** 表单配置查询入口，用于解析可信表单身份。 */
    private final EntityFormMapper formMapper;
    /** 列表配置查询入口，用于解析可信列表身份。 */
    private final EntityListConfigMapper listMapper;

    /**
     * 创建界面调用上下文工厂；结果供后续流程传递或持久化。
     *
     * @param definition 定义，作为 {@code CommonInvocationContext} 的输入影响后续处理
     * @param authorization 授权，作为 {@code definitionMapper.selectById} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于创建界面调用上下文工厂
     * @return 创建后的界面调用上下文工厂结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    public UiInvocationContext create(
            UiExtensionDefinition definition,
            UiDataSourceExecutionAuthorization authorization,
            UiExtensionExecuteRequest request) {
        Map<String, Object> input = request == null || request.getInput() == null
                ? Map.of()
                : request.getInput();
        EntityDefinition entity = definitionMapper.selectById(
                authorization.entityId());
        if (entity == null) {
            throw new IllegalStateException("接口执行关联实体不存在");
        }
        EntityDescriptor descriptor = new EntityDescriptor(
                entity.getId(),
                entity.getEntityCode(),
                entity.getEntityName(),
                entity.getStorageMode() == null
                        ? null
                        : entity.getStorageMode().name(),
                authorization.dataScopePlan() == null
                        ? null
                        : authorization.dataScopePlan().releaseVersion());
        CommonInvocationContext common = new CommonInvocationContext(
                definition.getId(),
                definition.getProviderOperationCode(),
                authorization.usage(),
                normalizedOwnerType(authorization.configType()),
                authorization.configId(),
                normalize(request == null ? null : request.getTargetType()),
                text(request == null ? null : request.getTargetKey()),
                authorization.user().getId(),
                authorization.user().getUsername(),
                authorization.user().getOrgId(),
                authorization.user().getOrgId(),
                authorization.user().getDeptId(),
                authorization.releaseId(),
                authorization.releaseVersion(),
                providerRequestId(authorization));

        return switch (definition.getOperationContextType()) {
            case "FORM" -> formContext(
                    common,
                    descriptor,
                    authorization,
                    request,
                    input);
            case "LIST" -> listContext(
                    common,
                    descriptor,
                    authorization,
                    request,
                    input);
            case "ENTITY" -> new EntityInvocationContext(
                    common,
                    descriptor,
                    normalize(authorization.usage()),
                    text(input.get("recordId")));
            default -> throw new IllegalStateException(
                    "接口扩展上下文类型无效: "
                            + definition.getOperationContextType());
        };
    }

    /**
     * 处理表单上下文，并将结果传给后续步骤。
     *
     * @param common {@code common}，作为 {@code FormInvocationContext} 的输入影响后续处理
     * @param entity 实体，作为 {@code FormInvocationContext} 的输入影响后续处理
     * @param authorization 授权，作为 {@code formMapper.selectById} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于处理表单上下文
     * @param input 待处理表单上下文的原始输入，结果供调用方继续使用
     * @return 处理后的表单上下文结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private FormInvocationContext formContext(
            CommonInvocationContext common,
            EntityDescriptor entity,
            UiDataSourceExecutionAuthorization authorization,
            UiExtensionExecuteRequest request,
            Map<String, Object> input) {
        EntityForm form = formMapper.selectById(authorization.configId());
        if (form == null) {
            throw new IllegalStateException("接口执行关联表单不存在");
        }
        Map<String, Object> parent = objectMap(
                input.get("parent"));
        Map<String, Object> row = objectMap(
                input.get("row"));
        boolean trustedFormButton = UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                normalize(authorization.usage()));
        if (trustedFormButton && (request == null
                || !StringUtils.hasText(request.getServerFormMode()))) {
            throw new IllegalStateException(
                    "表单按钮 Provider 上下文缺少已验证运行模式");
        }
        return new FormInvocationContext(
                common,
                entity,
                form.getId(),
                form.getFormKey(),
                form.getFormName(),
                trustedFormButton
                        ? request.getServerFormMode()
                        : firstText(input.get("mode"), "view"),
                trustedFormButton
                        ? request.getServerRecordId()
                        : text(input.get("recordId")),
                trustedFormButton
                        ? null
                        : firstText(
                                common.targetKey(),
                                input.get("fieldCode")),
                firstText(
                        input.get("parentRecordId"),
                        parent.get("recordId")),
                firstText(
                        input.get("rowKey"),
                        row.get("key"),
                        row.get("id"),
                        row.get("index")),
                trustedFormButton && request != null
                        ? request.getServerTaskId() : null,
                trustedFormButton && request != null
                        ? request.getServerProcessInstanceId() : null);
    }

    /**
     * 列出上下文；查询结果供调用方展示或继续处理。
     *
     * @param common {@code common}，作为 {@code ListInvocationContext} 的输入影响后续处理
     * @param entity 实体，作为 {@code ListInvocationContext} 的输入影响后续处理
     * @param authorization 授权，作为 {@code listMapper.selectById} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于列出上下文
     * @param input 待列出上下文的原始输入，结果供调用方继续使用
     * @return 符合条件的列表调用上下文结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private ListInvocationContext listContext(
            CommonInvocationContext common,
            EntityDescriptor entity,
            UiDataSourceExecutionAuthorization authorization,
            UiExtensionExecuteRequest request,
            Map<String, Object> input) {
        EntityListConfig list = listMapper.selectById(
                authorization.configId());
        if (list == null) {
            throw new IllegalStateException("接口执行关联列表不存在");
        }
        return new ListInvocationContext(
                common,
                entity,
                list.getId(),
                list.getListKey(),
                list.getListName(),
                request == null
                        ? integer(input.get("pageNum"))
                        : firstInteger(request.getPageNum(), input.get("pageNum")),
                request == null
                        ? integer(input.get("pageSize"))
                        : firstInteger(request.getPageSize(), input.get("pageSize")),
                firstText(common.targetKey(), input.get("fieldCode")),
                text(input.get("scene")));
    }

    /**
     * 生成规范化归属方类型文本，供后续匹配或展示。
     *
     * @param value 待处理规范化归属方类型的原始输入，结果供调用方继续使用
     * @return 处理后的规范化归属方类型文本，供调用方比较或展示
     */
    private String normalizedOwnerType(String value) {
        String normalized = normalize(value);
        return normalized.startsWith("ENTITY") ? "ENTITY" : normalized;
    }

    /**
     * 表单按钮的客户端 requestId 不能直接进入 Provider；运行时只下传服务端
     * 绑定租户、用户、发布和按钮身份后生成的可信种子，保证重试可稳定识别。
     *
     * @param authorization 授权，作为 {@code UiDataSourceUsages.FORM_BUTTON_CLICK.equals} 的输入影响后续处理
     * @return 处理后的提供者请求ID文本，供调用方比较或展示
     */
    private String providerRequestId(
            UiDataSourceExecutionAuthorization authorization) {
        return UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                normalize(authorization.usage()))
                && StringUtils.hasText(authorization.idempotencySeed())
                ? authorization.idempotencySeed()
                : UUID.randomUUID().toString();
    }

    /**
     * 处理首个整数，并将结果传给后续步骤。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个整数结果，供调用方继续处理
     */
    private Integer firstInteger(Object... values) {
        for (Object value : values) {
            Integer result = integer(value);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     */
    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (!StringUtils.hasText(text(value))) {
            return null;
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 整理对象映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理对象映射的原始输入，结果供调用方继续使用
     * @return 对象映射键值结果，供调用方继续处理
     */
    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, Object> result =
                new java.util.LinkedHashMap<>();
        source.forEach((key, child) ->
                result.put(String.valueOf(key), child));
        return result;
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化界面调用上下文工厂的原始输入，结果供调用方继续使用
     * @return 规范化后的界面调用上下文工厂文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }
}
