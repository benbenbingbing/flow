package com.workflow.contracts.entity.list.model;

import java.util.List;
import java.util.Map;

/**
 * 平台统一的数据范围查询计划。
 *
 * @param allowed 允许，保存在对象中供后续校验、查询或展示
 * @param sqlFragment SQL{@code fragment}，保存在对象中供后续校验、查询或展示
 * @param parameters 参数集合，保存在对象中供后续校验、查询或展示
 * @param requiredJoins 必填{@code joins}，保存在对象中供后续校验、查询或展示
 * @param matchedPolicies {@code matched}{@code policies}，保存在对象中供后续校验、查询或展示
 * @param explanation {@code explanation}，保存在对象中供后续校验、查询或展示
 * @param releaseVersion 发布版本号，后续用于校验快照一致性
 */
public record DataScopePlan(
        /** 是否允许访问 */
        boolean allowed,
        /** 拼接到 SQL 的条件片段 */
        String sqlFragment,
        /** SQL 片段绑定的参数 */
        Map<String, Object> parameters,
        /** 需要附加的 JOIN 片段 */
        List<String> requiredJoins,
        /** 命中的数据范围策略标识 */
        List<String> matchedPolicies,
        /** 计划说明 */
        String explanation,
        /** 发布版本号 */
        Integer releaseVersion) {
}
