package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * “关联内容”设计态资源。
 */
@Value
@Builder
public class UiViewCompositionDTO {

    String id;
    String ownerType;
    String ownerId;
    String compositionKey;
    String anchorType;
    String anchorKey;
    Map<String, Object> config;
    Long orderKey;
    Integer revision;
    /** 查询时为宿主当前修订号，变更后为本次事务提交的新修订号。 */
    Integer ownerRevision;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
