package com.workflow.entity.list.application;

/**
 * 列表运行时发布版本上下文。
 *
 * @param releaseId 发布版本 ID，后续用于解析固定配置
 * @param releaseVersion 发布版本号，后续用于校验快照一致性
 * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
 */
public record EntityListReleaseContext(
        String releaseId,
        Integer releaseVersion,
        String releaseResolutionToken) {

    /**
     * 处理当前，并将结果传给后续步骤。
     *
     * @return 处理后的当前结果，供调用方继续处理
     */
    public static EntityListReleaseContext current() {
        return new EntityListReleaseContext(null, null, null);
    }
}
