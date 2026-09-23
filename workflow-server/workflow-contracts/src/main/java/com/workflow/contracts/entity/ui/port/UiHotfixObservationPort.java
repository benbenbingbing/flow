package com.workflow.contracts.entity.ui.port;

/**
 * UI HOTFIX 观察指标端口。
 *
 * <p>运行时模块只上报稳定业务标识和成功结果；实现采用尽力而为语义，不能反向
 * 阻断表单或流程主链路。</p>
 */
public interface UiHotfixObservationPort {

    /**
     * 按实际生效的 UI 发布版本记录观察指标。
     *
     * @param releaseId 发布版本ID，后续用于记录发布版本指标时定位或关联目标
     * @param metricCode 指标编码，后续用于记录发布版本指标时定位或关联目标
     * @param successful 成功，供本方法记录发布版本指标时使用
     * @param errorMessage 错误消息，供本方法记录发布版本指标时使用
     */
    void recordReleaseMetric(
            String releaseId,
            String metricCode,
            boolean successful,
            String errorMessage);

    /**
     * 按配置记录指标，适用于加载失败而无法解析发布版本的场景。
     *
     * @param configType 配置类型标识，决定后续配置指标采用的处理分支
     * @param configId 配置ID，后续用于记录配置指标时定位或关联目标
     * @param metricCode 指标编码，后续用于记录配置指标时定位或关联目标
     * @param successful 成功，供本方法记录配置指标时使用
     * @param errorMessage 错误消息，供本方法记录配置指标时使用
     */
    void recordConfigMetric(
            String configType,
            String configId,
            String metricCode,
            boolean successful,
            String errorMessage);

    /**
     * 按流程发布历史记录任务办理指标，并映射到生效中的 HOTFIX。
     *
     * @param processVersionHistoryId 流程版本历史ID，后续用于记录流程版本指标时定位或关联目标
     * @param successful 成功，供本方法记录流程版本指标时使用
     * @param errorMessage 错误消息，供本方法记录流程版本指标时使用
     */
    void recordProcessVersionMetric(
            String processVersionHistoryId,
            boolean successful,
            String errorMessage);
}
