package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class EntityBatchController {

    @PostMapping("/api/entity/batch-publish")
    public Result<Void> batchPublish(@RequestBody List<String> ids) {
        return Result.success();
    }

    @PostMapping("/api/entity/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<String> ids) {
        return Result.success();
    }

    @PostMapping("/api/entity/import")
    public Result<Map<String, Object>> importEntity(@RequestBody Map<String, Object> dto) {
        dto.put("id", System.currentTimeMillis()); return Result.success(dto);
    }

    @GetMapping("/api/entity/{id}/export")
    public Result<Map<String, Object>> exportEntity(@PathVariable String id) {
        return Result.success(Map.of());
    }

    @GetMapping("/api/entity/{id}/compare/{versionId}")
    public Result<Map<String, Object>> compareEntity(@PathVariable String id, @PathVariable String versionId) {
        return Result.success(Map.of());
    }

    @PostMapping("/api/entity/{id}/merge")
    public Result<Map<String, Object>> mergeEntity(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success(Map.of());
    }

    @GetMapping("/api/entity/{id}/datasource")
    public Result<Map<String, Object>> getDatasource(@PathVariable String id) {
        return Result.success(Map.of("datasourceType", "MYSQL", "tableName", "entity_data_" + id));
    }

    @PutMapping("/api/entity/{id}/datasource")
    public Result<Void> updateDatasource(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @GetMapping("/api/entity/{id}/indexes")
    public Result<List<Map<String, Object>>> getIndexes(@PathVariable String id) {
        return Result.success(new java.util.ArrayList<>());
    }

    @GetMapping("/api/entity/{id}/history")
    public Result<List<Map<String, Object>>> getHistory(@PathVariable String id) {
        return Result.success(new java.util.ArrayList<>());
    }

    @GetMapping("/api/entity/{id}/fields")
    public Result<List<Map<String, Object>>> getFields(@PathVariable String id) {
        return Result.success(new java.util.ArrayList<>());
    }

    @GetMapping("/api/entity/{id}/fields/{fieldId}")
    public Result<Map<String, Object>> getFieldDetail(@PathVariable String id, @PathVariable String fieldId) {
        return Result.success(Map.of());
    }

    @PutMapping("/api/entity/{id}/fields/sort")
    public Result<Void> sortFields(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @PostMapping("/api/entity/{id}/fields/batch-delete")
    public Result<Void> batchDeleteFields(@PathVariable String id, @RequestBody List<String> fieldIds) {
        return Result.success();
    }
}
