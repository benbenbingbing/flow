package com.workflow.contracts.identity.model;

/**
 * 跨模块使用的最小用户目录信息。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param username 用户名称，后续用于身份匹配或操作展示
 * @param nickname 用户昵称，供界面展示
 * @param organizationId 组织 ID，供后续身份归属和访问判断
 * @param departmentId 部门 ID，供后续身份归属和访问判断
 */
public record IdentityUser(
        String id,
        String username,
        String nickname,
        String organizationId,
        String departmentId) {
}
