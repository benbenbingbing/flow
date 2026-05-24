package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SysBatchController {

    @GetMapping("/api/system/post/list")
    public Result<List<Map<String, Object>>> postList() {
        return Result.success(new java.util.ArrayList<>());
    }

    @GetMapping("/api/system/post/{id}")
    public Result<Map<String, Object>> postDetail(@PathVariable String id) {
        return Result.success(Map.of());
    }

    @PostMapping("/api/system/post")
    public Result<Map<String, Object>> savePost(@RequestBody Map<String, Object> dto) {
        dto.put("id", System.currentTimeMillis()); return Result.success(dto);
    }

    @PutMapping("/api/system/post/{id}")
    public Result<Map<String, Object>> updatePost(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        return Result.success(dto);
    }

    @DeleteMapping("/api/system/post/{id}")
    public Result<Void> deletePost(@PathVariable String id) {
        return Result.success();
    }

    @GetMapping("/api/system/org/{id}/users")
    public Result<List<Map<String, Object>>> orgUsers(@PathVariable String id) {
        return Result.success(new java.util.ArrayList<>());
    }

    @GetMapping("/api/system/role/1/permission")
    public Result<Map<String, Object>> rolePermission() {
        return Result.success(Map.of("menuIds", new java.util.ArrayList<>()));
    }

    @GetMapping("/api/system/role/1/users")
    public Result<List<Map<String, Object>>> roleUsers() {
        return Result.success(new java.util.ArrayList<>());
    }

    @PutMapping("/api/system/role/1/permission")
    public Result<Void> saveRolePermission(@RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @PutMapping("/api/system/user/1/role")
    public Result<Void> saveUserRole(@RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @GetMapping("/api/system/menu/permissions")
    public Result<List<Map<String, Object>>> menuPermissions() {
        return Result.success(new java.util.ArrayList<>());
    }
}
