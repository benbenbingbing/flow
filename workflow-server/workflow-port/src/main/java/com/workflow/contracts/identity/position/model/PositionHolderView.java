package com.workflow.contracts.identity.position.model;

import java.time.Instant;

/**
 * 组织节点下的有效职务任职人投影。
 *
 * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param username 用户名称，后续用于身份匹配或操作展示
 * @param displayName 用户可见名称，供界面和日志展示
 * @param primary 主要，保存在对象中供后续校验、查询或展示
 * @param sortOrder 排序权重，后续用于稳定展示顺序
 * @param effectiveFrom 有效起始，保存在对象中供后续校验、查询或展示
 */
public record PositionHolderView(
        String userId,
        String username,
        String displayName,
        boolean primary,
        int sortOrder,
        Instant effectiveFrom) {

    /**
     * 初始化位置持有者视图，保存构造参数供后续方法使用。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param displayName 用户可见名称，供界面和日志展示
     * @param primary 主要，保存在对象中供后续校验、查询或展示
     * @param sortOrder 排序权重，后续用于稳定展示顺序
     * @param effectiveFrom 有效起始，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public PositionHolderView {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("任职用户 ID 不能为空");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("任职用户名不能为空");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("任职生效时间不能为空");
        }
    }
}
