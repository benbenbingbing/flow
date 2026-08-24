package com.workflow.process.definition.api.response;

import java.util.List;

/**
 * 当前流程草稿与最近发布版本的结构化差异。
 *
 * @param baseVersion       比较基线版本，首次发布时为 0
 * @param changed           是否存在变化
 * @param metadataChanges   元数据变化字段
 * @param addedElementIds   新增 BPMN 元素 ID
 * @param removedElementIds 删除 BPMN 元素 ID
 * @param changedElementIds 内容发生变化的 BPMN 元素 ID
 */
public record ProcessDefinitionDiffDTO(
        int baseVersion,
        boolean changed,
        List<String> metadataChanges,
        List<String> addedElementIds,
        List<String> removedElementIds,
        List<String> changedElementIds) {
}
