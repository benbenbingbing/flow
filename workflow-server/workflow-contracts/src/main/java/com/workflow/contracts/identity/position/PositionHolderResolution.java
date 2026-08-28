package com.workflow.contracts.identity.position;

import java.util.List;

/**
 * 指定职务在某组织节点上的查询结果。
 *
 * @param resultCode 查询状态；只有 {@link PositionDirectoryResultCode#RESOLVED}
 *                   与 {@link PositionDirectoryResultCode#NO_ACTIVE_HOLDER} 属于正常目录结果
 * @param directoryRevision 目录修订标识，用于审计和前端变更提示
 */
public record PositionHolderResolution(
        PositionDirectoryResultCode resultCode,
        String positionCode,
        String organizationUnitId,
        List<PositionHolderView> holders,
        String directoryRevision) {

    public PositionHolderResolution {
        if (resultCode == null) {
            throw new IllegalArgumentException("职务人员查询状态不能为空");
        }
        holders = holders == null ? List.of() : List.copyOf(holders);
        if (resultCode == PositionDirectoryResultCode.RESOLVED
                && holders.isEmpty()) {
            throw new IllegalArgumentException("RESOLVED 结果必须包含任职人");
        }
        if (resultCode != PositionDirectoryResultCode.RESOLVED
                && !holders.isEmpty()) {
            throw new IllegalArgumentException("非 RESOLVED 结果不得包含任职人");
        }
    }

    public boolean resolved() {
        return resultCode == PositionDirectoryResultCode.RESOLVED;
    }
}
