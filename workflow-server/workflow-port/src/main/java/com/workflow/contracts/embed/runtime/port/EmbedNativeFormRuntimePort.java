package com.workflow.contracts.embed.runtime.port;

import java.util.Map;

/** 根据已认证 Embed 根表单和已发布组件路径恢复目标，不签发页面凭证。 */
public interface EmbedNativeFormRuntimePort {
    VerifiedTarget currentFormTarget(String entityCode, String formId);
    String configHash(String formId);
    /** rootFormId 必须来自服务端会话，componentPath 只提供定位且逐层验证。 */
    VerifiedTarget authorizeTarget(String rootFormId, String formId, Map<String,Object> runtimeContext);
    record VerifiedTarget(String entityCode, String formId, String formReleaseId, int formReleaseVersion) {}
}
