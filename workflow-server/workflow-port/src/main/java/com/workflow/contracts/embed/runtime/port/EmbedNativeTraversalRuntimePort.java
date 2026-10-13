package com.workflow.contracts.embed.runtime.port;

/**
 * 校验 Flow 原生关联内容关联路径，并解析服务端解析的下一跳运行时资产。
 */
public interface EmbedNativeTraversalRuntimePort {

    /**
     * 解析业务关联路径；每一层重新检查来源记录、目标配置和当前用户权限。
     *
     * @param compositionContext 业务根入口和逐层关联路径
     * @return 首跳宿主与当前固定目标
     */
    TraversalTarget resolve(java.util.Map<String,Object> compositionContext);

    /**
     * 服务端重新解析关联路径得到的根宿主、当前目标和实体坐标。
     *
     * @param rootOwnerType 根归属方类型标识，决定后续遍历目标采用的处理分支
     * @param rootOwnerId 根归属方ID，后续用于处理遍历目标时定位或关联目标
     * @param rootReleaseId 根发布版本ID，后续用于处理遍历目标时定位或关联目标
     * @param rootReleaseVersion 根发布版本，保存在对象中供后续校验、查询或展示
     * @param rootRecordId 根记录ID，后续用于处理遍历目标时定位或关联目标
     * @param targetOwnerType 目标归属方类型标识，决定后续遍历目标采用的处理分支
     * @param targetOwnerId 目标归属方ID，后续用于处理遍历目标时定位或关联目标
     * @param targetReleaseId 目标发布版本ID，后续用于处理遍历目标时定位或关联目标
     * @param targetReleaseVersion 目标发布版本，保存在对象中供后续校验、查询或展示
     * @param targetRecordId 目标记录ID，后续用于处理遍历目标时定位或关联目标
     * @param targetEntityCode 目标实体编码，后续用于处理遍历目标时定位或关联目标
     */
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
