package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/code-gen")
@RequiredArgsConstructor
public class CodeGenController {

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> dto) {
        return Result.success(Map.of("message", "代码生成成功"));
    }

    @GetMapping("/preview/{entityId}")
    public Result<Map<String, Object>> preview(@PathVariable String entityId) {
        return Result.success(Map.of("code", "// preview"));
    }

    @GetMapping("/download/{entityId}")
    public Result<Map<String, Object>> download(@PathVariable String entityId) {
        return Result.success(Map.of("url", "/download/" + entityId));
    }
}
