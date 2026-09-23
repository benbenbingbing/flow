package com.workflow.embed.domain;

/**
 * Security-relevant Embed View state.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
 * @param surfaceType 界面类型标识，决定后续嵌入式视图快照采用的处理分支
 * @param status 状态标识，决定后续嵌入式视图快照采用的处理分支
 * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
 */
public record EmbedViewSnapshot(
        String id,
        String viewKey,
        String surfaceType,
        String status,
        long securityVersion) {

    /**
     * 判断是否活动；判断结果决定调用方的后续分支。
     *
     * @return 活动条件成立时为 true，否则为 false
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
