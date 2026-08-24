package com.workflow.entity.permission.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 存量列表数据范围批量确认请求，单批最多处理 200 个对象。 */
@Data
public class EntityListScopeInventoryBatchConfirmRequest {
    @Valid
    @NotEmpty
    @Size(max = 200)
    private List<EntityListScopeInventoryConfirmItem> items;
}
