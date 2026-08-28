package com.workflow.contracts.identity.position;

/**
 * 组织职务目录查询的稳定结果码。
 */
public enum PositionDirectoryResultCode {
    RESOLVED,
    NO_ACTIVE_HOLDER,
    POSITION_NOT_FOUND,
    POSITION_DISABLED,
    ORGANIZATION_UNIT_NOT_FOUND,
    ORGANIZATION_UNIT_DISABLED,
    POSITION_UNIT_TYPE_MISMATCH
}
