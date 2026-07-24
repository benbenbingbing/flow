package com.workflow.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * UI 配置草稿与线上版本差异对比结果。
 */
@Value
@Builder
public class UiConfigDiffDTO {

    /** 配置类型（如 FORM / LIST / DATA_SOURCE 等） */
    String configType;
    /** 配置 ID */
    String configId;
    /** 草稿内容哈希 */
    String draftHash;
    /** 当前生效内容哈希 */
    String activeHash;
    /** 是否存在差异 */
    boolean changed;
    /** 存在变更的分区名称列表 */
    List<String> changedSections;
    /** 变更明细条目 */
    List<UiConfigDiffItemDTO> changedItems;
}
