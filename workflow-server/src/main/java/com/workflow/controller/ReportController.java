package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(new ArrayList<>());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@PathVariable String id) {
        return Result.success(Map.of());
    }

    @PostMapping
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> dto) {
        Map<String, Object> result = new HashMap<>(dto);
        result.put("id", System.currentTimeMillis());
        return Result.success(result);
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success(dto);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        return Result.success();
    }

    @PostMapping("/{id}/copy")
    public Result<Map<String, Object>> copy(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        Map<String, Object> result = new HashMap<>(dto);
        result.put("id", System.currentTimeMillis());
        return Result.success(result);
    }

    @GetMapping("/{id}/data")
    public Result<Map<String, Object>> data(@PathVariable String id) {
        return Result.success(Map.of("rows", new ArrayList<>(), "total", 0));
    }

    @GetMapping("/{id}/preview")
    public Result<Map<String, Object>> preview(@PathVariable String id) {
        return Result.success(Map.of());
    }

    @PostMapping("/{id}/share")
    public Result<Map<String, Object>> share(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success(Map.of("shareToken", "token_" + System.currentTimeMillis()));
    }

    @PutMapping("/{id}/datasource")
    public Result<Void> datasource(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @PutMapping("/{id}/fields")
    public Result<Void> fields(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @PutMapping("/{id}/conditions")
    public Result<Void> conditions(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @PutMapping("/{id}/chart")
    public Result<Void> chart(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @PostMapping("/{id}/schedule")
    public Result<Void> schedule(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }
}
