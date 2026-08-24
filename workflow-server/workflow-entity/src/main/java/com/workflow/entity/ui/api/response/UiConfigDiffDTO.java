package com.workflow.entity.ui.api.response;

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
    /** 是否包含当前表单或列表自身可撤销的草稿修改。 */
    boolean discardableChanged;
    /** 是否能在不修改继承配置和外部发布引用的前提下撤销本地草稿。 */
    boolean canDiscardDraft;
    /** 当前差异是否包含继承配置或外部发布引用漂移。 */
    boolean dependencyChanged;
    /** 不能撤销时的简要原因；可撤销时为空。 */
    String discardBlockedReason;
    /** 存在变更的分区名称列表 */
    List<String> changedSections;
    /** 变更明细条目 */
    List<UiConfigDiffItemDTO> changedItems;
}
