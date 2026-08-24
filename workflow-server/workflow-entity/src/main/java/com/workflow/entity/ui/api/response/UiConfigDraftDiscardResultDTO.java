package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

/**
 * 撤销未发布修改后的配置状态。
 */
@Value
@Builder
public class UiConfigDraftDiscardResultDTO {

    /** FORM 或 LIST。 */
    String configType;
    /** 表单或列表配置 ID。 */
    String configId;
    /** 被丢弃草稿的 canonical 哈希。 */
    String discardedDraftHash;
    /** 恢复后的草稿哈希。 */
    String draftHash;
    /** 当前激活发布的内容哈希；存在依赖漂移时可与 draftHash 不同。 */
    String publishedHash;
    /** 当前激活发布 ID。 */
    String activeReleaseId;
    /** 当前激活发布版本。 */
    Integer activeVersion;
    /** 撤销前配置修订号。 */
    Integer previousRevision;
    /** 撤销后配置修订号。 */
    Integer revision;
    /** 撤销本地修改后是否仍与 ACTIVE 发布存在差异。 */
    boolean remainingChanged;
    /** 剩余差异是否来自继承配置或外部发布引用。 */
    boolean dependencyChanged;
}
