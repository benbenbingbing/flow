package com.workflow.contracts.identity.model;

/**
 * 当前操作人的稳定跨模块表示。
 *
 * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param username 用户名称，后续用于身份匹配或操作展示
 */
public record CurrentActor(String userId, String username) {
}
