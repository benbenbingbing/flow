package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FormBatchController {

    @PostMapping("/api/entity-form/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<String> ids) {
        return Result.success();
    }

    @PostMapping("/api/entity-form/batch-publish")
    public Result<Void> batchPublish(@RequestBody List<String> ids) {
        return Result.success();
    }

    @PostMapping("/api/entity-form/import")
    public Result<Map<String, Object>> importForm(@RequestBody Map<String, Object> dto) {
        dto.put("id", System.currentTimeMillis()); return Result.success(dto);
    }

    @GetMapping("/api/entity-form/{id}/export")
    public Result<Map<String, Object>> exportForm(@PathVariable String id) {
        return Result.success(Map.of());
    }

    @GetMapping("/api/entity-form/{id}/versions")
    public Result<List<Map<String, Object>>> getVersions(@PathVariable String id) {
        return Result.success(new java.util.ArrayList<>());
    }

    @PostMapping("/api/entity-form/{id}/publish")
    public Result<Void> publish(@PathVariable String id) {
        return Result.success();
    }

    @PostMapping("/api/entity-form/{id}/rollback/{versionId}")
    public Result<Void> rollback(@PathVariable String id, @PathVariable String versionId) {
        return Result.success();
    }

    @GetMapping("/api/entity-form/{id}/render")
    public Result<Map<String, Object>> render(@PathVariable String id) {
        return Result.success(Map.of());
    }

    @GetMapping("/api/entity-form/{id}/preview")
    public Result<Map<String, Object>> preview(@PathVariable String id) {
        return Result.success(Map.of());
    }

    @PutMapping("/api/entity-form/{id}/fields/sort")
    public Result<Void> sortFields(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @PostMapping("/api/entity-form/{id}/fields/batch-delete")
    public Result<Void> batchDeleteFields(@PathVariable String id, @RequestBody List<String> fieldIds) {
        return Result.success();
    }

    @PutMapping("/api/entity-form/{id}/design")
    public Result<Void> design(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }
}
