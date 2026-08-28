package com.workflow.contracts.identity.position;

/**
 * 流程模块所需的最小职务定义投影。
 */
public record PositionDefinitionView(
        String positionCode,
        String positionName,
        String applicableUnitType,
        String holderMode,
        int revision) {

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
