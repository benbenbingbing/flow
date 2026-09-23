package com.workflow.embed.domain;

/**
 * 当前 Embed 写请求对共享幂等记录的认领结果。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param fencingToken {@code fencing}令牌，后续用于授权校验、关联或幂等去重
 * @param disposition {@code disposition}，保存在对象中供后续校验、查询或展示
 * @param responseStatus 响应状态标识，决定后续嵌入式幂等认领采用的处理分支
 * @param responseBody 响应请求体，保存在对象中供后续校验、查询或展示
 * @param resourceType 资源类型标识，决定后续嵌入式幂等认领采用的处理分支
 * @param resourceId 资源ID，后续用于处理嵌入式幂等认领时定位或关联目标
 */
public record EmbedIdempotencyClaim(
        String id,
        long fencingToken,
        Disposition disposition,
        Integer responseStatus,
        String responseBody,
        String resourceType,
        String resourceId) {

    /**
     * 判断{@code acquired}条件是否成立，供调用方选择后续分支。
     *
     * @return {@code acquired}条件成立时为 true，否则为 false
     */
    public boolean acquired() {
        return disposition == Disposition.ACQUIRED;
    }

    /**
     * 判断重放条件是否成立，供调用方选择后续分支。
     *
     * @return 重放条件成立时为 true，否则为 false
     */
    public boolean replay() {
        return disposition == Disposition.REPLAY;
    }

    /**
     * 判断处理条件是否成立，供调用方选择后续分支。
     *
     * @return 处理条件成立时为 true，否则为 false
     */
    public boolean processing() {
        return disposition == Disposition.PROCESSING;
    }

    /**
     * 定义{@code disposition}的可选值；调用方据此选择对应的处理分支。
     */
    public enum Disposition {
        ACQUIRED,
        REPLAY,
        PROCESSING
    }
}
