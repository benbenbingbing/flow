package com.workflow.embed.domain;

/**
 * 从已认证 Session 与不可变 Embed Release 恢复出的原生 Flow 表单坐标。
 *
 * <p>该值只在服务端请求链中创建，浏览器传入的同名字段永远不能覆盖它。</p>
 *
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param formId 表单 ID，后续用于定位已发布表单
 * @param formReleaseId 表单发布版本ID，后续用于处理嵌入式原生表单目标时定位或关联目标
 * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
 * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
 * @param listReleaseId 列表发布版本ID，后续用于处理嵌入式原生表单目标时定位或关联目标
 * @param listReleaseVersion 列表发布版本，保存在对象中供后续校验、查询或展示
 * @param entryMode 入口模式标识，决定后续嵌入式原生表单目标采用的处理分支
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
 * @param initialData 初始数据，保存在对象中供后续校验、查询或展示
 * @param parameters 参数集合，保存在对象中供后续校验、查询或展示
 * @param context 执行上下文，向后续嵌入式原生表单目标步骤传递身份、配置或状态
 */
public record EmbedNativeFormTarget(
        String entityCode,
        String formId,
        String formReleaseId,
        Integer formReleaseVersion,
        String listKey,
        String listReleaseId,
        Integer listReleaseVersion,
        String entryMode,
        String recordId,
        String processInstanceId,
        java.util.Map<String, Object> initialData,
        java.util.Map<String, Object> parameters,
        java.util.Map<String, Object> context) {
}
