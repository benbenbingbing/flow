package com.workflow.contracts.embed.runtime.port;

import com.workflow.contracts.embed.EmbedNativeListDependencyClosure;
import java.time.Instant;

/**
 * 为 Embed iframe 复用 Flow 原生 Published List 签发会话固定上下文。
 */
public interface EmbedNativeListRuntimePort {

    /** 为固定列表发布目标签发会话绑定的解析令牌。 */
    String issueReleaseResolutionToken(Target target);

    /** 验证令牌与当前 Embed Session/View Release/列表坐标完全一致。 */
    void verifyReleaseResolutionToken(String token, Target target);

    /** 从不可变 Embed Runtime Snapshot 恢复的列表根坐标。 */
    record Target(
            String entityCode,
            String listKey,
            String listReleaseId,
            int listReleaseVersion,
            String sessionId,
            String viewId,
            String viewReleaseId,
            Instant sessionAbsoluteExpiresAt,
            int dependencyClosureVersion,
            String dependencyClosureHash,
            EmbedNativeListDependencyClosure dependencyClosure) {

        /** 兼容不涉及 open-list 的内部调用；生产根令牌必须携带闭包。 */
        public Target(
                String entityCode,
                String listKey,
                String listReleaseId,
                int listReleaseVersion,
                String sessionId,
                String viewReleaseId,
                Instant sessionAbsoluteExpiresAt) {
            this(entityCode, listKey, listReleaseId, listReleaseVersion,
                    sessionId, null, viewReleaseId, sessionAbsoluteExpiresAt,
                    0, null, null);
        }

        /** 委托请求复验只需 Session/View/列表坐标，闭包引用从签名 claims 恢复。 */
        public Target(
                String entityCode,
                String listKey,
                String listReleaseId,
                int listReleaseVersion,
                String sessionId,
                String viewId,
                String viewReleaseId,
                Instant sessionAbsoluteExpiresAt) {
            this(entityCode, listKey, listReleaseId, listReleaseVersion,
                    sessionId, viewId, viewReleaseId, sessionAbsoluteExpiresAt,
                    0, null, null);
        }
    }
}
