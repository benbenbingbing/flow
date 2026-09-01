package com.workflow.embed.domain;

/**
 * 从已认证 Session 与不可变 Embed Release 恢复出的原生 Flow 表单坐标。
 *
 * <p>该值只在服务端请求链中创建，浏览器传入的同名字段永远不能覆盖它。</p>
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
