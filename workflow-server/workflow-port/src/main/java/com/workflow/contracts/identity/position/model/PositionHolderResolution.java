package com.workflow.contracts.identity.position.model;

import java.util.List;

/**
 * 指定职务在某组织节点上的查询结果。
 *
 * @param resultCode 查询状态；只有 {@link PositionDirectoryResultCode#RESOLVED}
 *                   与 {@link PositionDirectoryResultCode#NO_ACTIVE_HOLDER} 属于正常目录结果
 * @param positionCode 位置编码，后续用于处理位置持有者解析时定位或关联目标
 * @param organizationUnitId 组织单元ID，后续用于处理位置持有者解析时定位或关联目标
 * @param holders 持有者集合，保存在对象中供后续校验、查询或展示
 * @param directoryRevision 目录修订标识，用于审计和前端变更提示
 */
public record PositionHolderResolution(
        PositionDirectoryResultCode resultCode,
        String positionCode,
        String organizationUnitId,
        List<PositionHolderView> holders,
        String directoryRevision) {

    /**
     * 初始化位置持有者解析，保存构造参数供后续方法使用。
     *
     * @param resultCode 结果编码，后续用于初始化位置持有者解析时定位或关联目标
     * @param positionCode 位置编码，后续用于初始化位置持有者解析时定位或关联目标
     * @param organizationUnitId 组织单元ID，后续用于初始化位置持有者解析时定位或关联目标
     * @param holders 持有者集合，保存在对象中供后续校验、查询或展示
     * @param directoryRevision 目录修订号，后续用于判断身份数据是否过期
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 判断已解析条件是否成立，供调用方选择后续分支。
     *
     * @return 已解析条件成立时为 true，否则为 false
     */
    public boolean resolved() {
        return resultCode == PositionDirectoryResultCode.RESOLVED;
    }
}
