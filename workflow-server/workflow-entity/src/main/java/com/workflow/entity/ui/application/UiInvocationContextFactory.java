package com.workflow.entity.ui.application;

import com.workflow.contracts.ui.CommonInvocationContext;
import com.workflow.contracts.ui.EntityDescriptor;
import com.workflow.contracts.ui.EntityInvocationContext;
import com.workflow.contracts.ui.FormInvocationContext;
import com.workflow.contracts.ui.ListInvocationContext;
import com.workflow.contracts.ui.UiInvocationContext;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
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

    public UiInvocationContext create(
            UiDataSourceDefinition definition,
            UiDataSourceExecutionAuthorization authorization,
            UiDataSourceExecuteRequest request) {
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
                definition.getOperationCode(),
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
                    "接口操作上下文类型无效: "
                            + definition.getOperationContextType());
        };
    }

    private FormInvocationContext formContext(
            CommonInvocationContext common,
            EntityDescriptor entity,
            UiDataSourceExecutionAuthorization authorization,
            UiDataSourceExecuteRequest request,
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

    private ListInvocationContext listContext(
            CommonInvocationContext common,
            EntityDescriptor entity,
            UiDataSourceExecutionAuthorization authorization,
            UiDataSourceExecuteRequest request,
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

    private String normalizedOwnerType(String value) {
        String normalized = normalize(value);
        return normalized.startsWith("ENTITY") ? "ENTITY" : normalized;
    }

    /**
     * 表单按钮的客户端 requestId 不能直接进入 Provider；运行时只下传服务端
     * 绑定租户、用户、发布和按钮身份后生成的可信种子，保证重试可稳定识别。
     */
    private String providerRequestId(
            UiDataSourceExecutionAuthorization authorization) {
        return UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                normalize(authorization.usage()))
                && StringUtils.hasText(authorization.idempotencySeed())
                ? authorization.idempotencySeed()
                : UUID.randomUUID().toString();
    }

    private Integer firstInteger(Object... values) {
        for (Object value : values) {
            Integer result = integer(value);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

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

    private String firstText(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

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

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }
}
