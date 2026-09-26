package com.workflow.contracts.entity.ui.model;

/**
 * 发布表单快照中固定的直接子表单引用。
 *
 * @param formId 表单 ID，后续用于定位已发布表单
 * @param releaseId 发布版本 ID，后续用于解析固定配置
 * @param releaseVersion 发布版本号，后续用于校验快照一致性
 */
public record UiPublishedFormReference(
        String formId,
        String releaseId,
        Integer releaseVersion) {
}
