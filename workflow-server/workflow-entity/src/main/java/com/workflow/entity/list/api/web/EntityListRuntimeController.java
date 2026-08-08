package com.workflow.entity.list.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.entity.list.api.request.EntityListQueryRequest;
import com.workflow.entity.list.api.response.EntityListSchemaDTO;
import com.workflow.entity.permission.api.response.EntityListScopeSimulationDTO;
import com.workflow.entity.permission.api.request.EntityListScopeSimulationRequest;
import com.workflow.entity.list.application.EntityListRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实体列表运行态控制器。
 * <p>面向运行态提供列表 schema 获取、列表数据查询及数据范围模拟接口。
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/entity-lists")
@RequiredArgsConstructor
public class EntityListRuntimeController {

    private final EntityListRuntimeService runtimeService;

    /**
     * 获取列表运行态 schema（字段、动作、场景等）。GET /api/entity-lists/{entityCode}/{listKey}/schema
     *
     * @param entityCode 实体编码
     * @param listKey    列表标识
     * @param scene      场景标识（可选）
     * @return 列表 schema 结构
     */
    @GetMapping("/{entityCode}/{listKey}/schema")
    public Result<EntityListSchemaDTO> schema(
            @PathVariable String entityCode,
            @PathVariable String listKey,
            @RequestParam(required = false) String scene) {
        return Result.success(runtimeService.schema(entityCode, listKey, scene));
    }

    /**
     * 查询列表数据。POST /api/entity-lists/{entityCode}/{listKey}/query
     *
     * @param entityCode 实体编码
     * @param listKey    列表标识
     * @param request    查询请求（含条件、分页、排序）
     * @return 查询结果（分页或列表）
     */
    @PostMapping("/{entityCode}/{listKey}/query")
    public Result<Object> query(
            @PathVariable String entityCode,
            @PathVariable String listKey,
            @RequestBody(required = false) EntityListQueryRequest request) {
        return Result.success(runtimeService.query(entityCode, listKey, request));
    }

    /**
     * 模拟数据范围策略对查询结果的影响。POST /api/entity-lists/{entityCode}/{listKey}/scope-simulation
     *
     * @param entityCode 实体编码
     * @param listKey    列表标识
     * @param request    模拟请求（含待模拟的角色/策略）
     * @return 模拟结果（命中范围与命中数据预览）
     */
    @PostMapping("/{entityCode}/{listKey}/scope-simulation")
    public Result<EntityListScopeSimulationDTO> simulate(
            @PathVariable String entityCode,
            @PathVariable String listKey,
            @RequestBody(required = false) EntityListScopeSimulationRequest request) {
        return Result.success(runtimeService.simulate(entityCode, listKey, request));
    }
}
