package com.workflow.entity.form.application.model;

import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;

/**
 * 本次请求已解析的实体表单当前配置。
 *
 * <p>封装当前表单定义与内容摘要；版本和钉选字段仅保留旧 DTO 协议兼容，
 * 供表单运行时解析使用。</p>
 *
 * @param form           表单定义
 * @param releaseId      当前配置内容摘要，用于提交一致性检查
 * @param releaseVersion 旧协议占位，当前配置固定为 1
 * @param pinned         旧协议占位，当前配置固定为 false
 * @param effectiveReleaseId 当前配置摘要，与 releaseId 相同
 * @param effectiveContentHash 实际生效快照哈希
 * @param hotfixTargetId 旧协议占位，当前配置始终为 null
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
     * @param releaseVersion 旧协议占位，当前配置固定为 1，后续用于校验快照一致性
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
     * @param releaseId      当前配置内容摘要，用于提交一致性检查
     * @param releaseVersion 旧协议占位，当前配置固定为 1
     */
    public ResolvedEntityFormRelease(
            EntityForm form,
            String releaseId,
            Integer releaseVersion) {
        this(form, releaseId, releaseVersion, false);
    }

    /**
     * 读取旧协议标志；当前解析器不会产生热修复目标。
     *
     * @return 旧对象兼容值，当前配置始终为 false
     */
    public boolean hotfixApplied() {
        return hotfixTargetId != null;
    }
}
