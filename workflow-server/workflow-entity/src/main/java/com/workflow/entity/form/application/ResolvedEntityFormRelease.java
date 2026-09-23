package com.workflow.entity.form.application;

import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;

/**
 * 已解析的实体表单发布版本信息。
 *
 * <p>封装表单定义、发布ID、发布版本号以及是否为钉选发布，
 * 供表单运行时解析使用。</p>
 *
 * @param form           表单定义
 * @param releaseId      发布记录ID
 * @param releaseVersion 发布版本号
 * @param pinned         是否为钉选发布
 * @param effectiveReleaseId 实际生效的热修复发布ID；无热修复时等于 releaseId
 * @param effectiveContentHash 实际生效快照哈希
 * @param hotfixTargetId 热修复目标ID
 * @param purpose 解析目的
 */
public record ResolvedEntityFormRelease(
        EntityForm form,
        String releaseId,
        Integer releaseVersion,
        boolean pinned,
        String effectiveReleaseId,
        String effectiveContentHash,
        String hotfixTargetId,
        UiRuntimePurpose purpose) {

    /**
     * 初始化已解析实体表单发布版本，保存构造参数供后续方法使用。
     *
     * @param form 表单，保存在对象中供后续校验、查询或展示
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     * @param pinned 固定，保存在对象中供后续校验、查询或展示
     */
    public ResolvedEntityFormRelease(
            EntityForm form,
            String releaseId,
            Integer releaseVersion,
            boolean pinned) {
        this(
                form,
                releaseId,
                releaseVersion,
                pinned,
                releaseId,
                null,
                null,
                pinned
                        ? UiRuntimePurpose.HISTORICAL
                        : UiRuntimePurpose.STANDALONE);
    }

    /**
     * 构造非钉定的发布版本信息。
     *
     * @param form           表单定义
     * @param releaseId      发布记录ID
     * @param releaseVersion 发布版本号
     */
    public ResolvedEntityFormRelease(
            EntityForm form,
            String releaseId,
            Integer releaseVersion) {
        this(form, releaseId, releaseVersion, false);
    }

    /**
     * 判断热修复{@code applied}条件是否成立，供调用方选择后续分支。
     *
     * @return 热修复{@code applied}条件成立时为 true，否则为 false
     */
    public boolean hotfixApplied() {
        return hotfixTargetId != null;
    }
}
