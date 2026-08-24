package com.workflow.entity.permission.api.web;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.permission.api.dto.EntityListScopeInventoryBatchResult;
import com.workflow.entity.permission.api.dto.EntityListScopeInventoryDTO;
import com.workflow.entity.permission.api.request.EntityListScopeInventoryBatchConfirmRequest;
import com.workflow.entity.permission.application.EntityListScopeInventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 存量列表数据范围安全策略盘点接口。 */
@RestController
@RequestMapping("/api/entity-list-scope-inventory")
@RequiredArgsConstructor
@RequiresPermission("entity:list-scope:inventory")
public class EntityListScopeInventoryController {

    private final EntityListScopeInventoryService inventoryService;
    private final CurrentUserRoleService currentUserRoleService;

    /** 分页查询盘点清单。 */
    @GetMapping
    public ApiResponse<PageResult<EntityListScopeInventoryDTO>> page(
            @RequestParam(required = false) String processingStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        requireAdministrator();
        return ApiResponse.success(
                inventoryService.findPage(processingStatus, keyword, pageNum, pageSize));
    }

    /** 重新扫描尚未纳入盘点的存量列表。 */
    @PostMapping("/refresh")
    public ApiResponse<Integer> refresh() {
        requireAdministrator();
        return ApiResponse.success(inventoryService.refreshInventory());
    }

    /** 原子批量确认所选盘点对象。 */
    @PostMapping("/batch-confirm")
    public ApiResponse<EntityListScopeInventoryBatchResult> batchConfirm(
            @Valid @RequestBody EntityListScopeInventoryBatchConfirmRequest request) {
        requireAdministrator();
        return ApiResponse.success(inventoryService.batchConfirm(request));
    }

    private void requireAdministrator() {
        currentUserRoleService.requireAdministrator("仅管理员可以治理存量列表数据范围策略");
    }
}
