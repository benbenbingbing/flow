package com.workflow.contracts.ui.hotfix;

/**
 * UI HOTFIX 观察指标端口。
 *
 * <p>运行时模块只上报稳定业务标识和成功结果，不感知治理表结构；
 * 指标实现必须采用尽力而为语义，不能反向阻断表单或流程主链路。</p>
 */
public interface UiHotfixObservationPort {

    /** 按实际生效的 UI 发布版本记录观察指标。 */
    void recordReleaseMetric(
            String releaseId,
            String metricCode,
            boolean successful,
            String errorMessage);

    /** 按配置记录指标，适用于加载失败而无法解析发布版本的场景。 */
    void recordConfigMetric(
            String configType,
            String configId,
            String metricCode,
            boolean successful,
            String errorMessage);

    /** 按流程发布历史记录任务办理指标，并映射到生效中的 HOTFIX。 */
    void recordProcessVersionMetric(
            String processVersionHistoryId,
            boolean successful,
            String errorMessage);
}
