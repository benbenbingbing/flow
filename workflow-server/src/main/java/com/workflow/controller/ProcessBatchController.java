package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProcessBatchController {

    @PostMapping("/api/process/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<String> ids) {
        return Result.success();
    }

    @PostMapping("/api/process/batch-publish")
    public Result<Void> batchPublish(@RequestBody List<String> ids) {
        return Result.success();
    }

    @PostMapping("/api/process/batch-disable")
    public Result<Void> batchDisable(@RequestBody List<String> ids) {
        return Result.success();
    }

    @PostMapping("/api/process/import")
    public Result<Map<String, Object>> importProcess(@RequestBody Map<String, Object> dto) {
        dto.put("id", System.currentTimeMillis()); return Result.success(dto);
    }

    @GetMapping("/api/process/{id}/export")
    public Result<Map<String, Object>> exportProcess(@PathVariable String id) {
        return Result.success(Map.of());
    }

    @GetMapping("/api/process/category/list")
    public Result<List<Map<String, Object>>> categoryList() {
        return Result.success(new java.util.ArrayList<>());
    }

    @PostMapping("/api/process/category")
    public Result<Map<String, Object>> saveCategory(@RequestBody Map<String, Object> dto) {
        dto.put("id", System.currentTimeMillis()); return Result.success(dto);
    }

    @PostMapping("/api/process/{id}/permission")
    public Result<Void> savePermission(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @PostMapping("/api/process/{id}/trigger")
    public Result<Void> saveTrigger(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @GetMapping("/api/process/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(Map.of("total", 0, "published", 0, "draft", 0));
    }

    @PostMapping("/api/process/{id}/test-parse")
    public Result<Map<String, Object>> testParse(@PathVariable String id) {
        return Result.success(Map.of("valid", true));
    }

    @GetMapping("/api/process/template/list")
    public Result<List<Map<String, Object>>> templateList() {
        return Result.success(new java.util.ArrayList<>());
    }

    @PostMapping("/api/process/template/{id}/apply")
    public Result<Map<String, Object>> applyTemplate(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        dto.put("id", System.currentTimeMillis()); return Result.success(dto);
    }
}
