package com.workflow.port.system.model.identity;

/**
 * 来源同步的显式绑定修复参数。调用方先核实来源对象与本地身份的对应关系。
 *
 * @param localId 已确认的用户或组织主键，用于保留原有业务引用并校验本次修复目标
 * @param previousExternalId 同来源旧外部 ID，提供时只迁移匹配的旧绑定，为 null 时只补绑未绑定的对象
 */
public record IdentityBindingChange(String localId, String previousExternalId) {
}
