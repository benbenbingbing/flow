package com.workflow.contracts.identity.model;

import lombok.Builder;

/**
 * 组织基础快照，父 ID 属于同一来源，根节点使用 null，路径和层级由框架维护。
 * externalCode 原样用作 orgCode，必填且最多 100 字符，全局唯一，创建后固定，供跨环境配置引用。
 */
@Builder
public record OrganizationBaseData(String externalCode, String name, OrganizationUnitType type, String parentExternalId,
        String businessLevelCode, String phone, String email, String address,
        String description, int sortOrder, boolean enabled) {
}
