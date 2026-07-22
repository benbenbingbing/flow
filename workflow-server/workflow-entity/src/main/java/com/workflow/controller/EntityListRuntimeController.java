package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.EntityListQueryRequest;
import com.workflow.dto.EntityListSchemaDTO;
import com.workflow.dto.permission.EntityListScopeSimulationDTO;
import com.workflow.dto.permission.EntityListScopeSimulationRequest;
import com.workflow.service.EntityListRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/entity-lists")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityListRuntimeController {

    private final EntityListRuntimeService runtimeService;

    @GetMapping("/{entityCode}/{listKey}/schema")
    public Result<EntityListSchemaDTO> schema(
            @PathVariable String entityCode,
            @PathVariable String listKey,
            @RequestParam(required = false) String scene) {
        return Result.success(runtimeService.schema(entityCode, listKey, scene));
    }

    @PostMapping("/{entityCode}/{listKey}/query")
    public Result<Object> query(
            @PathVariable String entityCode,
            @PathVariable String listKey,
            @RequestBody(required = false) EntityListQueryRequest request) {
        return Result.success(runtimeService.query(entityCode, listKey, request));
    }

    @PostMapping("/{entityCode}/{listKey}/scope-simulation")
    public Result<EntityListScopeSimulationDTO> simulate(
            @PathVariable String entityCode,
            @PathVariable String listKey,
            @RequestBody(required = false) EntityListScopeSimulationRequest request) {
        return Result.success(runtimeService.simulate(entityCode, listKey, request));
    }
}
