package com.workflow.contracts.ui.hotfix;

/**
 * UI 热修复查询流程影响范围的端口。
 *
 * <p>实体配置模块只依赖该端口，不直接依赖流程模块的 Flowable、Mapper 或实体。</p>
 */
public interface UiHotfixProcessImpactPort {

    /**
     * 查询引用指定表单的当前可发起流程版本与仍有运行实例的历史版本。
     *
     * @param formId 表单ID
     * @return 流程影响快照
     */
    UiHotfixProcessImpact analyzeFormImpact(String formId);
}
