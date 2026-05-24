package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/notice")
@RequiredArgsConstructor
public class SysNoticeController {

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
}
