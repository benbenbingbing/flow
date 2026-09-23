package com.workflow.embed.domain;

/**
 * Immutable release material needed during launch and exchange.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param revision 修订版本，保存在对象中供后续校验、查询或展示
 * @param surfaceType 界面类型标识，决定后续嵌入式发布版本快照采用的处理分支
 * @param entryModesJson 入口模式集合JSON，保存在对象中供后续校验、查询或展示
 * @param capabilitiesJson 能力集合JSON，保存在对象中供后续校验、查询或展示
 * @param contextSchemaJson 上下文结构JSON，保存在对象中供后续校验、查询或展示
 * @param uiConfigJson 界面配置JSON，保存在对象中供后续校验、查询或展示
 */
public record EmbedReleaseSnapshot(
        String id,
        long revision,
        String surfaceType,
        String entryModesJson,
        String capabilitiesJson,
        String contextSchemaJson,
        String uiConfigJson) {
}
