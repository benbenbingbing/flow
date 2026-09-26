package com.workflow.contracts.identity.model;

/**
 * 跨模块使用的最小用户组目录信息。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param code 业务编码，供后续匹配和引用
 * @param name 展示名称，供界面或日志识别
 */
public record IdentityGroup(String id, String code, String name) {
}
