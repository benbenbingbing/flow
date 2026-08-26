package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * “使用一条真实数据测试”的安全结果。
 */
@Value
@Builder
public class UiViewCompositionTestDTO {

    /** 当前用户是否通过设计态和真实数据访问校验。 */
    boolean authorized;
    /** 权限过滤后匹配到的目标记录总数。 */
    long matchedCount;
    /** 可读配置摘要。 */
    String summary;
    /** 无样本、无匹配或接口返回等具体说明。 */
    String description;
    /** 仅返回已授权来源记录的最小标识和标题，不返回整行业务数据。 */
    Map<String, Object> sourceRecord;
    /** 最多返回十个已授权目标记录 ID。 */
    List<String> targetRecordIds;
    /** 实际追加到目标查询的等值条件。 */
    Map<String, Object> filters;
}
