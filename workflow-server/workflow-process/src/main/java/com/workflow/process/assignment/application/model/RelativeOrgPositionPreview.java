package com.workflow.process.assignment.application.model;

import com.workflow.contracts.identity.position.model.OrganizationUnitSnapshot;
import com.workflow.contracts.identity.position.model.PositionHolderView;

import java.util.List;

/**
 * 相对组织职务的权威试算结果，由运行时同一解析器生成。
 *
 * @param anchorUnit 锚点单元，保存在对象中供后续校验、查询或展示
 * @param scannedUnits {@code scanned}{@code units}，保存在对象中供后续校验、查询或展示
 * @param matchedUnit {@code matched}单元，保存在对象中供后续校验、查询或展示
 * @param holders 持有者集合，保存在对象中供后续校验、查询或展示
 * @param resultCode 结果编码，后续用于处理相对组织位置预览时定位或关联目标
 * @param reasonCode 原因编码，后续用于处理相对组织位置预览时定位或关联目标
 * @param reasonMessage 原因消息，保存在对象中供后续校验、查询或展示
 * @param warnings {@code warnings}，保存在对象中供后续校验、查询或展示
 * @param directoryRevision 目录修订号，后续用于判断身份数据是否过期
 */
public record RelativeOrgPositionPreview(
        OrganizationUnitSnapshot anchorUnit,
        List<ScannedUnit> scannedUnits,
        MatchedUnit matchedUnit,
        List<PositionHolderView> holders,
        String resultCode,
        String reasonCode,
        String reasonMessage,
        List<String> warnings,
        String directoryRevision) {

    /**
     * 初始化相对组织位置预览，保存构造参数供后续方法使用。
     *
     * @param anchorUnit 锚点单元，保存在对象中供后续校验、查询或展示
     * @param scannedUnits {@code scanned}{@code units}，保存在对象中供后续校验、查询或展示
     * @param matchedUnit {@code matched}单元，保存在对象中供后续校验、查询或展示
     * @param holders 持有者集合，保存在对象中供后续校验、查询或展示
     * @param resultCode 结果编码，后续用于初始化相对组织位置预览时定位或关联目标
     * @param reasonCode 原因编码，后续用于初始化相对组织位置预览时定位或关联目标
     * @param reasonMessage 原因消息，保存在对象中供后续校验、查询或展示
     * @param warnings {@code warnings}，保存在对象中供后续校验、查询或展示
     * @param directoryRevision 目录修订号，后续用于判断身份数据是否过期
     */
    public RelativeOrgPositionPreview {
        scannedUnits = scannedUnits == null ? List.of() : List.copyOf(scannedUnits);
        holders = holders == null ? List.of() : List.copyOf(holders);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * 封装{@code scanned}单元的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续{@code scanned}单元采用的处理分支
     * @param depth 深度，保存在对象中供后续校验、查询或展示
     * @param result 结果，保存在对象中供后续校验、查询或展示
     */
    public record ScannedUnit(
            String id,
            String name,
            String type,
            int depth,
            String result) {
    }

    /**
     * 封装{@code matched}单元的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续{@code matched}单元采用的处理分支
     * @param depth 深度，保存在对象中供后续校验、查询或展示
     */
    public record MatchedUnit(
            String id,
            String name,
            String type,
            int depth) {
    }
}
