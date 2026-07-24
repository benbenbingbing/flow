package com.workflow.service.permission;

/**
 * 数据范围发布需要人工复核异常。
 *
 * <p>当历史数据范围方案存在无法自动迁移的复杂规则时抛出，
 * 提示调用方需重新保存方案后再发布。</p>
 */
public class EntityListScopeManualReviewRequiredException extends IllegalStateException {

    /**
     * 构造异常实例。
     *
     * @param message 描述需要人工复核原因的消息
     */
    public EntityListScopeManualReviewRequiredException(String message) {
        super(message);
    }
}
