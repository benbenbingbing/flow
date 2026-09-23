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

    /**
     * 处理最新，并将结果传给后续步骤。
     *
     * @param entityId 实体ID，后续用于处理最新时定位或关联目标
     * @return 处理后的最新结果，供调用方继续处理
     */
    @GetMapping("/{entityId}/latest")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntitySchemaOperationDTO> latest(@PathVariable String entityId) {
        return ApiResponse.success(operationService.latest(entityId));
    }

    /**
     * 处理重试，并将结果传给后续步骤。
     *
     * @param entityId 实体ID，后续用于处理重试时定位或关联目标
     * @return 处理后的重试结果，供调用方继续处理
     */
    @PostMapping("/{entityId}/retry")
    @RequiresPermission("entity:definition:publish")
    public ApiResponse<EntitySchemaOperationDTO> retry(@PathVariable String entityId) {
        return ApiResponse.success(operationService.retry(entityId));
    }

    /**
     * 终止实体结构操作；后续读取或执行将使用更新后的状态。
     *
     * @param entityId 实体ID，后续用于终止实体结构操作时定位或关联目标
     * @return 终止后的实体结构操作结果，供调用方继续处理
     */
    @PostMapping("/{entityId}/terminate")
    @RequiresPermission("entity:definition:publish")
    public ApiResponse<EntitySchemaOperationDTO> terminate(@PathVariable String entityId) {
        return ApiResponse.success(operationService.terminate(entityId));
    }
}
