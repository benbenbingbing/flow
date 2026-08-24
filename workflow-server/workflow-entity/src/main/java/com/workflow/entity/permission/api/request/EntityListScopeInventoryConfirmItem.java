package com.workflow.entity.permission.api.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 单个存量列表的治理确认内容。 */
@Data
public class EntityListScopeInventoryConfirmItem {
    @NotBlank
    private String listId;
    @NotBlank
    private String ownerId;
    @NotBlank
    private String ownerName;
    @NotBlank
    private String selectedPolicy;
    private String reason;
}
