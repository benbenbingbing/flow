package com.workflow.contracts.embed.runtime.port;

/**
 * 校验 Flow 原生关联内容遍历令牌，并解析令牌固定的下一跳运行时资产。
 */
public interface EmbedNativeTraversalRuntimePort {

    /**
     * 解析签名遍历上下文；令牌无效、过期或不属于当前用户时应拒绝请求。
     *
     * @param token Flow 关联内容运行时签发的不透明遍历令牌
     * @return 首跳宿主与当前固定目标
     */
    TraversalTarget resolve(String token);

    /** 签名遍历令牌固定的根宿主、当前目标和实体坐标。 */
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
