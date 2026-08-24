package com.workflow.process.definition.api.response;

import java.time.Instant;
import java.util.List;

/**
 * 流程发布预检结果。
 *
 * @param processId            流程配置 ID
 * @param revision             当前草稿修订号
 * @param draftHash            当前草稿哈希
 * @param basePublishedVersion 草稿基于的发布版本
 * @param publishable          是否允许发布
 * @param blockerCount         阻断项数量
 * @param warningCount         警告项数量
 * @param issues               全部预检问题
 * @param diff                 与最近发布版本的差异
 * @param activeInstanceCount  活跃实例数；无法统计时为 -1
 * @param affectedVersionCount 已发布历史版本数量
 * @param previewToken         绑定本次草稿与预检结论的令牌
 * @param generatedAt          预检生成时间
 */
public record ProcessPublishPreviewDTO(
        String processId,
        long revision,
        String draftHash,
        int basePublishedVersion,
        boolean publishable,
        long blockerCount,
        long warningCount,
        List<ProcessValidationIssueDTO> issues,
        ProcessDefinitionDiffDTO diff,
        long activeInstanceCount,
        int affectedVersionCount,
        String previewToken,
        Instant generatedAt) {
}
