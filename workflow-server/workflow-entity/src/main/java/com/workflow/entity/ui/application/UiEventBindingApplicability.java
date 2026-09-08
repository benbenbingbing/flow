package com.workflow.entity.ui.application;

import com.workflow.contracts.ui.UiDataSourceUsages;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 实体默认 UI 事件可以被哪类页面消费的统一规则。
 *
 * <p>该规则同时供可选接口操作、发布快照和引用查询使用，避免 ENTITY
 * 设计入口只允许 ENTITY 操作、而实际运行请求始终为 FORM/LIST 的矛盾。</p>
 */
final class UiEventBindingApplicability {

    private static final Set<String> FORM_EVENTS = Set.of(
            UiDataSourceUsages.DETAIL_LOAD,
            UiDataSourceUsages.DATA_CREATE,
            UiDataSourceUsages.DATA_UPDATE,
            UiDataSourceUsages.FORM_OPEN,
            UiDataSourceUsages.FORM_SAVE,
            UiDataSourceUsages.FORM_RESET,
            UiDataSourceUsages.FIELD_CHANGE,
            UiDataSourceUsages.ENTITY_SELECTED,
            UiDataSourceUsages.FIELD_BUTTON_CLICK,
            UiDataSourceUsages.SUBFORM_LOAD,
            UiDataSourceUsages.SUBFORM_SAVE,
            UiDataSourceUsages.FORM_BUTTON_CLICK);

    private static final Set<String> LIST_EVENTS = Set.of(
            UiDataSourceUsages.LIST_LOAD,
            UiDataSourceUsages.LIST_EXPORT,
            UiDataSourceUsages.DETAIL_LOAD,
            UiDataSourceUsages.DATA_CREATE,
            UiDataSourceUsages.DATA_UPDATE,
            UiDataSourceUsages.DATA_DELETE,
            UiDataSourceUsages.DATA_BATCH_DELETE,
            UiDataSourceUsages.TOOLBAR_BUTTON_CLICK,
            UiDataSourceUsages.ROW_BUTTON_CLICK);

    private UiEventBindingApplicability() {
    }

    /** 返回事件可能实际触发的页面上下文，顺序固定为 FORM、LIST。 */
    static Set<String> contextsForEvent(String eventCode) {
        Set<String> result = new LinkedHashSet<>();
        if (FORM_EVENTS.contains(eventCode)) {
            result.add("FORM");
        }
        if (LIST_EVENTS.contains(eventCode)) {
            result.add("LIST");
        }
        return Set.copyOf(result);
    }

    static boolean appliesTo(String eventCode, String configType) {
        return contextsForEvent(eventCode).contains(configType);
    }
}
