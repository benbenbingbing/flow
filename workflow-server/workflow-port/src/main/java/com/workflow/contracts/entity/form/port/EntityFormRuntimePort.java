package com.workflow.contracts.entity.form.port;

import com.workflow.contracts.entity.form.model.EntityFormBinding;
import com.workflow.contracts.entity.form.model.EntityFormRuntimeContext;
import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;

import java.util.Map;
import java.util.Optional;

/**
 * 流程节点解析实体表单时使用的稳定只读能力。
 *
 * <p>表单绑定与运行模型统一放在 {@code entity.form.model}，调用方不依赖实体表单的存储实现。</p>
 */
public interface EntityFormRuntimePort {

    /**
     * 查询上下文；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 匹配的上下文；未找到时为空
     */
    Optional<EntityFormRuntimeContext> findContext(String entityCode);

    /**
     * 按ID查询{@code map<string,}{@code object>}；结果供后续展示或处理。
     *
     * @param formId 表单ID，后续用于查询表单ID时定位或关联目标
     * @return 表单ID键值结果，供调用方继续处理
     */
    Map<String, Object> findFormById(String formId);

    /**
     * 按绑定查询{@code map<string,}{@code object>}；结果供后续展示或处理。
     *
     * @param binding 绑定，供本方法查询表单绑定时使用
     * @param processVersionHistoryId 流程版本历史ID，后续用于查询表单绑定时定位或关联目标
     * @param purpose 用途，供本方法查询表单绑定时使用
     * @return 表单绑定键值结果，供调用方继续处理
     */
    Map<String, Object> findFormByBinding(
            EntityFormBinding binding,
            String processVersionHistoryId,
            UiRuntimePurpose purpose);

    /**
     * 使用可包含确切 ACTIVE_TASK 主体的可信上下文解析表单。
     * 默认实现保留旧适配器兼容；实体模块实现应保留全部主体字段用于签发令牌。
     *
     * @param binding 绑定，供本方法查询表单绑定时使用
     * @param context 执行上下文，向后续表单绑定步骤传递身份、配置或状态
     * @return 表单绑定键值结果，供调用方继续处理
     */
    default Map<String, Object> findFormByBinding(
            EntityFormBinding binding,
            UiRuntimeResolutionContext context) {
        return findFormByBinding(
                binding,
                context == null ? null
                        : context.processVersionHistoryId(),
                context == null ? UiRuntimePurpose.HISTORICAL
                        : context.purpose());
    }

    /**
     * 校验并获取当前绑定新数据；不满足约束时阻止后续处理。
     *
     * @param binding 绑定，供本方法校验并获取当前绑定新数据时使用
     * @param processVersionHistoryId 流程版本历史ID，后续用于校验并获取当前绑定新数据时定位或关联目标
     */
    void requireCurrentBindingForNewData(
            EntityFormBinding binding,
            String processVersionHistoryId);
}
