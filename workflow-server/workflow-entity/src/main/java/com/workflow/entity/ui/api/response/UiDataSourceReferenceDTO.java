package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 接口服务的一个草稿或仅发布事件步骤引用及其运行态状态。
 *
 * <p>一条事件绑定可以包含多个步骤，因此引用粒度为“绑定 + 步骤”。
 * 实体默认绑定可能被多个表单或列表继承，其逐页面状态保存在
 * {@link #contexts}，顶层状态仅做不丢失语义的汇总。</p>
 */
@Value
@Builder
public class UiDataSourceReferenceDTO {

    String referenceId;
    String referenceType;
    String usageCode;

    String bindingId;
    /** true 为当前草稿引用，false 为仅存在于激活发布快照的引用。 */
    boolean draftPresent;
    Integer draftRevision;
    boolean bindingEnabled;

    String serviceId;
    String serviceCode;
    String serviceName;
    String operationCode;
    String operationName;
    String operationKind;
    String operationContextType;

    String scopeType;
    String scopeId;
    String ownerType;
    String ownerId;
    String ownerName;
    String configType;
    String configId;
    String configName;

    String entityId;
    String entityCode;
    String entityName;
    String formId;
    String formKey;
    String formName;
    String listId;
    String listKey;
    String listName;

    String targetType;
    String targetKey;
    String targetName;
    String eventCode;
    String inheritanceMode;
    String inheritanceSource;

    Integer stepIndex;
    String stepCode;
    String stepName;
    Integer stepOrder;
    String stepStrategy;
    String failurePolicy;

    String publicationStatus;
    String publicationReason;
    /**
     * 生命周期：DRAFT_ONLY/PUBLISHED_MATCH/PUBLISHED_CHANGED/
     * PUBLISHED_ONLY/UNPUBLISHED。
     */
    String lifecycleStatus;
    boolean published;
    Boolean draftMatchesPublished;
    String effectiveStatus;
    String effectiveReason;
    /** 该引用步骤是否在所有相关线上上下文中生效；多上下文不一致时为空。 */
    Boolean effective;
    /** 该引用步骤确实在线上生效的 FORM/LIST 显示名称。 */
    List<String> effectiveContexts;
    /** 单一发布上下文的激活版本；实体默认绑定跨多个上下文时为空。 */
    String activeReleaseId;
    Integer activeReleaseVersion;
    int relevantContextCount;
    int publishedContextCount;
    int effectiveContextCount;
    List<UiDataSourceReferenceContextDTO> contexts;
}
