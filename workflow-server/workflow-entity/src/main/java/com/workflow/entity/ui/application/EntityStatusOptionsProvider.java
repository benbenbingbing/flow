package com.workflow.entity.ui.application;

import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.contracts.entity.ui.context.UiInvocationContext;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.entity.definition.application.EntityStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/** 将实体状态目录投影为通用选择项，供表单按当前实体加载。 */
@Component
@RequiredArgsConstructor
public class EntityStatusOptionsProvider implements UiDataSourceProvider {
    static final String EXTENSION_ID = "platform_entity_status_options";
    private static final String CODE = "entity_status_options";

    private final EntityStatusService statuses;

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "实体状态";
    }

    @Override
    public ExtensionImplementationOrigin implementationOrigin() {
        return ExtensionImplementationOrigin.PLATFORM;
    }

    /** 从已验证的表单上下文确定实体，返回按实体配置排序的编码与显示名称。 */
    @Override
    public Object execute(UiInvocationContext context, DataScopePlan dataScopePlan,
            Map<String, Object> configuration, Map<String, Object> input) {
        if (!UiDataSourceUsages.FIELD_OPTIONS.equals(context.usage())) {
            throw new IllegalArgumentException("实体状态数据源仅供字段选项使用");
        }
        // 实体归属由宿主授权阶段解析，表单业务输入中的 entityCode 不能改变查询范围。
        return statuses.findByEntityCode(context.entityCode()).stream()
                .filter(status -> StringUtils.hasText(status.getStatusCode()))
                .map(status -> Map.of("value", status.getStatusCode(), "label",
                        StringUtils.hasText(status.getStatusName())
                                ? status.getStatusName() : status.getStatusCode()))
                .toList();
    }
}
