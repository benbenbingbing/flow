package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 代码生成控制器。
 * 提供代码生成、预览与下载的 REST 接口（当前为示例桩实现）。
 */
@RestController
@RequestMapping("/api/code-gen")
@RequiredArgsConstructor
public class CodeGenController {

    /**
     * 触发代码生成。
     *
     * @param dto 代码生成请求参数
     * @return 生成结果
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> dto) {
        return Result.success(Map.of("message", "代码生成成功"));
    }

    /**
     * 预览指定实体的生成代码。
     *
     * @param entityId 实体ID
     * @return 预览代码
     */
    @GetMapping("/preview/{entityId}")
    public Result<Map<String, Object>> preview(@PathVariable String entityId) {
        return Result.success(Map.of("code", "// preview"));
    }

    /**
     * 获取指定实体生成代码的下载地址。
     *
     * @param entityId 实体ID
     * @return 包含下载地址的结果
     */
    @GetMapping("/download/{entityId}")
    public Result<Map<String, Object>> download(@PathVariable String entityId) {
        return Result.success(Map.of("url", "/download/" + entityId));
    }
}
