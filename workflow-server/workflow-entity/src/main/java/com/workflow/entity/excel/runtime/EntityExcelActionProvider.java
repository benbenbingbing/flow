package com.workflow.entity.excel.runtime;

import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.contracts.entity.ui.context.UiInvocationContext;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.ui.application.UiEventOriginContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 无业务写入的 Excel 准备接口；上传和保存发生在后续 POST 请求中。 */
@Component
@RequiredArgsConstructor
public class EntityExcelActionProvider implements UiDataSourceProvider {
    // 接口运行器启动时会收集全部 Provider，而解析器又依赖使用该运行器的列表服务。
    // 只在可信按钮实际执行时获取解析器，避免 Provider 注册阶段形成 Bean 构造循环。
    private final ObjectProvider<EntityExcelRuntimeResolver> resolver;
    @Override public String getCode() { return "entity_excel_action"; }
    @Override public String getDisplayName() { return "实体Excel导入导出"; }
    @Override public ExtensionImplementationOrigin implementationOrigin() { return ExtensionImplementationOrigin.PLATFORM; }

    @Override
    public Object execute(UiInvocationContext context, DataScopePlan dataScopePlan,
            Map<String, Object> configuration, Map<String, Object> input) {
        var origin = UiEventOriginContext.current();
        if (origin == null || !Objects.equals(context.configId(), origin.request().getConfigId())
                || !Objects.equals(context.common().targetKey(), origin.request().getTargetKey())
                || !Objects.equals(context.entityCode(), origin.chain().entityCode()))
            throw new ForbiddenException("Excel接口缺少可信按钮上下文");
        return Map.of("effects", List.of(resolver.getObject().prepareCustom(context.common().extensionId(), configuration)));
    }
}
