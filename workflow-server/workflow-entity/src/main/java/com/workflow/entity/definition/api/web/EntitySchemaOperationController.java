package com.workflow.entity.definition.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.definition.api.response.EntitySchemaOperationDTO;
import com.workflow.entity.definition.application.EntitySchemaOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 实体结构发布操作查询与人工处置入口。 */
@AuthenticatedApi
@RestController
@RequestMapping("/api/entity-schema-operation")
@RequiredArgsConstructor
public class EntitySchemaOperationController {

    private final EntitySchemaOperationService operationService;

    @GetMapping("/{entityId}/latest")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntitySchemaOperationDTO> latest(@PathVariable String entityId) {
        return ApiResponse.success(operationService.latest(entityId));
    }

    @PostMapping("/{entityId}/retry")
    @RequiresPermission("entity:definition:publish")
    public ApiResponse<EntitySchemaOperationDTO> retry(@PathVariable String entityId) {
        return ApiResponse.success(operationService.retry(entityId));
    }

    @PostMapping("/{entityId}/terminate")
    @RequiresPermission("entity:definition:publish")
    public ApiResponse<EntitySchemaOperationDTO> terminate(@PathVariable String entityId) {
        return ApiResponse.success(operationService.terminate(entityId));
    }
}
