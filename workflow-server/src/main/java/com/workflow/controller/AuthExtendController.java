package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.vo.LoginUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthExtendController {

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> dto) {
        return Result.success(Map.of("token", "new_token_" + System.currentTimeMillis()));
    }

    @GetMapping("/user-info")
    public Result<Map<String, Object>> userInfo() {
        Map<String, Object> user = new HashMap<>();
        user.put("id", "1");
        user.put("username", "admin");
        user.put("nickname", "管理员");
        return Result.success(user);
    }

    @GetMapping("/menus")
    public Result<List<Map<String, Object>>> menus() {
        return Result.success(new ArrayList<>());
    }

    @GetMapping("/buttons")
    public Result<List<String>> buttons() {
        return Result.success(List.of("add", "edit", "delete"));
    }

    @PostMapping("/captcha/verify")
    public Result<Map<String, Object>> verifyCaptcha(@RequestBody Map<String, String> dto) {
        return Result.success(Map.of("valid", true));
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> dto) {
        return Result.success(Map.of("id", System.currentTimeMillis()));
    }

    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@RequestBody Map<String, String> dto) {
        return Result.success();
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody Map<String, String> dto) {
        return Result.success();
    }

    @PostMapping("/lock")
    public Result<Void> lock(@RequestBody Map<String, String> dto) {
        return Result.success();
    }

    @PostMapping("/unlock")
    public Result<Void> unlock(@RequestBody Map<String, String> dto) {
        return Result.success();
    }

    @PostMapping("/logout-all")
    public Result<Void> logoutAll() {
        return Result.success();
    }

    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> devices() {
        return Result.success(new ArrayList<>());
    }

    @DeleteMapping("/device/{id}")
    public Result<Void> removeDevice(@PathVariable String id) {
        return Result.success();
    }

    @GetMapping("/login-log")
    public Result<List<Map<String, Object>>> loginLog() {
        return Result.success(new ArrayList<>());
    }

    @GetMapping("/operation-log")
    public Result<List<Map<String, Object>>> operationLog() {
        return Result.success(new ArrayList<>());
    }

    @GetMapping("/exception-log")
    public Result<List<Map<String, Object>>> exceptionLog() {
        return Result.success(new ArrayList<>());
    }

    @DeleteMapping("/log/clear")
    public Result<Void> clearLog() {
        return Result.success();
    }

    @GetMapping("/qr-code")
    public Result<Map<String, Object>> qrCode() {
        return Result.success(Map.of("token", "qr_" + System.currentTimeMillis()));
    }

    @GetMapping("/qr-code/status")
    public Result<Map<String, Object>> qrCodeStatus(@RequestParam String token) {
        return Result.success(Map.of("status", "waiting"));
    }

    @PostMapping("/social-login")
    public Result<Map<String, Object>> socialLogin(@RequestBody Map<String, String> dto) {
        return Result.success(Map.of("token", "social_token"));
    }
}
