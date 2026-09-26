package com.workflow.contracts.embed.runtime.port;

import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure;
import java.time.Instant;

/**
 * 为 Embed iframe 复用 Flow 原生 Published List 签发会话固定上下文。
 */
public interface EmbedNativeListRuntimePort {

    /**
     * 为固定列表发布目标签发会话绑定的解析令牌。
     *
     * @param target 目标，供本方法处理签发发布版本解析令牌时使用
     * @return 处理后的签发发布版本解析令牌文本，供调用方比较或展示
     */
    String issueReleaseResolutionToken(Target target);

    /**
     * 验证令牌与当前 Embed Session/View Release/列表坐标完全一致。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @param target 目标，供本方法验证发布版本解析令牌时使用
     */
    void verifyReleaseResolutionToken(String token, Target target);

    /**
     * 从不可变 Embed Runtime Snapshot 恢复的列表根坐标。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param listReleaseId 列表发布版本ID，后续用于处理目标时定位或关联目标
     * @param listReleaseVersion 列表发布版本，保存在对象中供后续校验、查询或展示
     * @param sessionId 会话ID，后续用于处理目标时定位或关联目标
     * @param viewId 视图ID，后续用于处理目标时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于处理目标时定位或关联目标
     * @param sessionAbsoluteExpiresAt 会话绝对过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param dependencyClosureVersion 依赖闭包版本，保存在对象中供后续校验、查询或展示
     * @param dependencyClosureHash 依赖闭包哈希，保存在对象中供后续校验、查询或展示
     * @param dependencyClosure 依赖闭包，保存在对象中供后续校验、查询或展示
     */
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

        /**
         * 兼容不涉及 open-list 的内部调用；生产根令牌必须携带闭包。
         *
         * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
         * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
         * @param listReleaseId 列表发布版本ID，后续用于初始化目标时定位或关联目标
         * @param listReleaseVersion 列表发布版本，保存在对象中供后续校验、查询或展示
         * @param sessionId 会话ID，后续用于初始化目标时定位或关联目标
         * @param viewReleaseId 视图发布版本ID，后续用于初始化目标时定位或关联目标
         * @param sessionAbsoluteExpiresAt 会话绝对过期时间，后续用于判断有效期或展示该事件的发生时间
         */
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

        /**
         * 委托请求复验只需 Session/View/列表坐标，闭包引用从签名 claims 恢复。
         *
         * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
         * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
         * @param listReleaseId 列表发布版本ID，后续用于初始化目标时定位或关联目标
         * @param listReleaseVersion 列表发布版本，保存在对象中供后续校验、查询或展示
         * @param sessionId 会话ID，后续用于初始化目标时定位或关联目标
         * @param viewId 视图ID，后续用于初始化目标时定位或关联目标
         * @param viewReleaseId 视图发布版本ID，后续用于初始化目标时定位或关联目标
         * @param sessionAbsoluteExpiresAt 会话绝对过期时间，后续用于判断有效期或展示该事件的发生时间
         */
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
