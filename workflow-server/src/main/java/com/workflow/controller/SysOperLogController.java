package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/operlog")
@RequiredArgsConstructor
public class SysOperLogController {

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(new ArrayList<>());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@PathVariable String id) {
        return Result.success(Map.of());
    }

    @DeleteMapping("/clean")
    public Result<Void> clean() {
        return Result.success();
    }
}
