package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserExtendController {

    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        Map<String, Object> user = new HashMap<>();
        user.put("id", "1");
        user.put("username", "admin");
        user.put("nickname", "管理员");
        return Result.success(user);
    }

    @PostMapping("/avatar")
    public Result<Map<String, Object>> avatar() {
        return Result.success(Map.of("url", "/avatar/default.png"));
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody Map<String, String> dto) {
        return Result.success();
    }

    @PutMapping("/bind-phone")
    public Result<Void> bindPhone(@RequestBody Map<String, String> dto) {
        return Result.success();
    }

    @PutMapping("/bind-email")
    public Result<Void> bindEmail(@RequestBody Map<String, String> dto) {
        return Result.success();
    }

    @PostMapping("/import")
    public Result<Void> importData() {
        return Result.success();
    }

    @GetMapping("/template")
    public Result<Map<String, Object>> template() {
        return Result.success(Map.of());
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<String> ids) {
        return Result.success();
    }

    @PutMapping("/status/batch")
    public Result<Void> batchStatus(@RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @PutMapping("/password/batch-reset")
    public Result<Void> batchResetPassword(@RequestBody Map<String, Object> dto) {
        return Result.success();
    }
}
