package com.workflow.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * UI 配置差异明细条目。
 * 描述某个分区下单个条目的变更信息。
 */
@Value
@Builder
public class UiConfigDiffItemDTO {

    /** 所属分区名称 */
    String section;
    /** 条目 ID */
    String id;
    /** 条目显示名称 */
    String label;
    /** 变更类型（ADD/MODIFY/REMOVE） */
    String changeType;
    /** 发生变更的字段列表 */
    List<String> changedFields;
}
