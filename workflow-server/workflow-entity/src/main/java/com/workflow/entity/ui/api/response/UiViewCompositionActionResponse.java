package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/** 已发布关联内容动作的权威执行结果。 */
@Value
@Builder
public class UiViewCompositionActionResponse {

    String operationId;
    String action;
    String sourceRecordId;
    /** SELECT 回填只返回发布配置允许的当前表单字段补丁。 */
    @Builder.Default
    Map<String, Object> sourcePatch = Map.of();
    /** LINK/UNLINK 的逐记录执行摘要，不返回整行敏感数据。 */
    @Builder.Default
    List<UiViewCompositionChangedReferenceDTO> changedReferences = List.of();
    /** 发布声明、关系类型和安全提交边界共同计算出的能力。 */
    @Builder.Default
    Map<String, UiViewCompositionActionCapabilityDTO> actionCapabilities =
            Map.of();
    /** 显式动作接口按发布输出映射返回的界面结果，不包含实体整行。 */
    @Builder.Default
    Map<String, Object> actionResult = Map.of();
    /** 写动作是否全部由实体变更管道的持久化回执重放。 */
    boolean replayed;
}
