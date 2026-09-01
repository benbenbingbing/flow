package com.workflow.contracts.embed;

/**
 * 校验 Flow 原生关联内容遍历令牌，并解析令牌固定的下一跳运行时资产。
 *
 * <p>Embed 模块只依赖该窄端口，不解析或信任浏览器提供的 entity/form/list
 * 坐标。实现必须验证签名、有效期和当前映射用户，并从持久化定义反查目标实体。</p>
 */
public interface EmbedNativeTraversalRuntimePort {

    /**
     * 解析签名遍历上下文；令牌无效、过期或不属于当前用户时必须抛出拒绝异常。
     *
     * @param token Flow 关联内容运行时签发的不透明遍历令牌
     * @return 首跳宿主与当前固定目标
     */
    TraversalTarget resolve(String token);

    record TraversalTarget(
            String rootOwnerType,
            String rootOwnerId,
            String rootReleaseId,
            Integer rootReleaseVersion,
            String rootRecordId,
            String targetOwnerType,
            String targetOwnerId,
            String targetReleaseId,
            Integer targetReleaseVersion,
            String targetRecordId,
            String targetEntityCode) {
    }
}
