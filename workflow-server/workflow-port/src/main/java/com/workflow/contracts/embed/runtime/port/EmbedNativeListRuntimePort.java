package com.workflow.contracts.embed.runtime.port;

import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure;
import java.time.Instant;

/**
 * 为 Embed iframe 复用 Flow 原生 Published List 校验服务端会话固定目标。
 */
public interface EmbedNativeListRuntimePort {

    /** 校验服务器保存的固定列表及依赖闭包，返回内容摘要。 */
    String configHash(Target target);
    /** 核对当前宿主 ID 与服务端固定列表属于同一资源。 */
    void requireListOwner(Target target, String ownerId);
    /** 从当前认证会话根闭包重新选择目标，浏览器不能指定发布坐标。 */
    Target authorizeTarget(Target root, String entityCode, String listKey);
    /** 从已认证表单根的发布组件路径恢复子列表，不接受请求自行选择版本或其他宿主。 */
    ReferencedList authorizeFormList(String trustedRootFormId, String entityCode, String listKey,
                                    java.util.Map<String, Object> runtimeContext);

    /** 已校验的子列表坐标只用于服务端后续读取；客户端不提供这组发布坐标。 */
    record ReferencedList(String entityCode, String listKey, String releaseId, int releaseVersion) {}

    /** 列表打开表单时核实服务端默认表单或发布按钮的目标。 */
    String authorizeForm(Target root, String entityCode, String listKey, String formId, java.util.Map<String,Object> runtimeContext);

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
         * 兼容不涉及 open-list 的内部调用；生产根配置必须携带闭包。
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
         * 委托请求复验只需 Session/View/列表坐标，闭包引用从服务端快照 恢复。
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
