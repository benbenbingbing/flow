package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.entity.EntityForm;
import com.workflow.service.entity.EntityFormResolveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实体表单解析控制器
 */
@RestController
@RequestMapping("/api/entity-form-resolve")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityFormResolveController {

    private final EntityFormResolveService entityFormResolveService;

    /**
     * 获取实体数据新增时使用的表单
     * 逻辑：
     * 1. 如果实体绑定了流程，获取流程的第一个任务节点绑定的表单
     * 2. 如果没有绑定表单，返回实体的默认表单
     * 3. 如果没有默认表单，返回 null
     *
     * @param entityCode 实体编码
     * @return 表单信息，包含表单字段
     */
    @GetMapping("/new-data/{entityCode}")
    public ApiResponse<EntityForm> getFormForNewData(@PathVariable String entityCode) {
        EntityForm form = entityFormResolveService.resolveFormForNewData(entityCode);
        return ApiResponse.success(form);
    }

    /**
     * 根据实体数据ID获取当前流程节点对应的表单
     * 用于查看数据详情时，根据数据所在流程节点显示不同的表单
     *
     * @param entityCode 实体编码
     * @param entityDataId 实体数据ID
     * @return 表单信息，包含当前任务节点信息
     */
    @GetMapping("/view-data/{entityCode}/{entityDataId}")
    public ApiResponse<EntityForm> getFormForViewData(
            @PathVariable String entityCode,
            @PathVariable Long entityDataId) {
        EntityForm form = entityFormResolveService.resolveFormForViewData(entityCode, entityDataId);
        return ApiResponse.success(form);
    }
}
