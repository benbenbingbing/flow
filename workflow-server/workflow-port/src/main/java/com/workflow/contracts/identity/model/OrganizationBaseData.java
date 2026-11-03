package com.workflow.contracts.identity.model;

import lombok.Builder;

/** 组织基础快照；父 ID 属于同一来源，根节点使用 null，路径和层级由框架维护。 */
@Builder
public record OrganizationBaseData(String externalCode, String name, OrganizationUnitType type, String parentExternalId,
        String businessLevelCode, String phone, String email, String address,
        String description, int sortOrder, boolean enabled) {
}
