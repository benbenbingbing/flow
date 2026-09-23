package com.workflow.embed.domain;

/**
 * Immutable release and presentation material required by one authenticated runtime request.
 *
 * @param releaseId 发布版本 ID，后续用于解析固定配置
 * @param viewId 视图ID，后续用于处理嵌入式运行时发布版本快照时定位或关联目标
 * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
 * @param viewName 视图名称，后续用于处理嵌入式运行时发布版本快照时匹配或展示
 * @param revision 修订版本，保存在对象中供后续校验、查询或展示
 * @param surfaceType 界面类型标识，决定后续嵌入式运行时发布版本快照采用的处理分支
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
 * @param listReleaseId 列表发布版本ID，后续用于处理嵌入式运行时发布版本快照时定位或关联目标
 * @param listReleaseVersion 列表发布版本，保存在对象中供后续校验、查询或展示
 * @param formReleaseId 表单发布版本ID，后续用于处理嵌入式运行时发布版本快照时定位或关联目标
 * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
 * @param capabilitiesJson 能力集合JSON，保存在对象中供后续校验、查询或展示
 * @param fieldPolicyJson 字段策略JSON，保存在对象中供后续校验、查询或展示
 * @param actionPolicyJson 动作策略JSON，保存在对象中供后续校验、查询或展示
 * @param contextBindingsJson 上下文绑定集合JSON，保存在对象中供后续校验、查询或展示
 * @param uiConfigJson 界面配置JSON，保存在对象中供后续校验、查询或展示
 * @param configJson 配置JSON，保存在对象中供后续校验、查询或展示
 * @param actorDisplayName 操作人展示名称，后续用于处理嵌入式运行时发布版本快照时匹配或展示
 * @param uiLocale 界面{@code locale}，保存在对象中供后续校验、查询或展示
 * @param uiTheme 界面{@code theme}，保存在对象中供后续校验、查询或展示
 * @param uiFormPresentation 界面表单展示，保存在对象中供后续校验、查询或展示
 */
public record EmbedRuntimeReleaseSnapshot(
        String releaseId,
        String viewId,
        String viewKey,
        String viewName,
        long revision,
        String surfaceType,
        String entityCode,
        String listKey,
        String listReleaseId,
        Integer listReleaseVersion,
        String formReleaseId,
        Integer formReleaseVersion,
        String capabilitiesJson,
        String fieldPolicyJson,
        String actionPolicyJson,
        String contextBindingsJson,
        String uiConfigJson,
        String configJson,
        String actorDisplayName,
        String uiLocale,
        String uiTheme,
        String uiFormPresentation) {
}
