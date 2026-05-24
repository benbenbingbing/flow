package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(new ArrayList<>());
    }

    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable String id) {
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        return Result.success();
    }
}
