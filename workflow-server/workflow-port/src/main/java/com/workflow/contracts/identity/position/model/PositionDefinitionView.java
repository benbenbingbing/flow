package com.workflow.contracts.identity.position.model;

/**
 * 流程模块所需的最小职务定义投影。
 *
 * @param positionCode 位置编码，后续用于处理位置定义视图时定位或关联目标
 * @param positionName 位置名称，后续用于处理位置定义视图时匹配或展示
 * @param applicableUnitType 适用单元类型标识，决定后续位置定义视图采用的处理分支
 * @param holderMode 持有者模式标识，决定后续位置定义视图采用的处理分支
 * @param revision 修订版本，保存在对象中供后续校验、查询或展示
 */
public record PositionDefinitionView(
        String positionCode,
        String positionName,
        String applicableUnitType,
        String holderMode,
        int revision) {

    /**
     * 初始化位置定义视图，保存构造参数供后续方法使用。
     *
     * @param positionCode 位置编码，后续用于初始化位置定义视图时定位或关联目标
     * @param positionName 位置名称，后续用于初始化位置定义视图时匹配或展示
     * @param applicableUnitType 适用单元类型标识，决定后续位置定义视图采用的处理分支
     * @param holderMode 持有者模式标识，决定后续位置定义视图采用的处理分支
     * @param revision 修订版本，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public PositionDefinitionView {
        if (positionCode == null || positionCode.isBlank()) {
            throw new IllegalArgumentException("职务编码不能为空");
        }
        if (positionName == null || positionName.isBlank()) {
            throw new IllegalArgumentException("职务名称不能为空");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("职务目录版本必须大于 0");
        }
    }
}
