package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.EntityStatus;
import com.workflow.service.EntityStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体状态控制器
 */
@RestController
@RequestMapping("/api/entity-status")
@RequiredArgsConstructor
public class EntityStatusController {
    
    private final EntityStatusService entityStatusService;
    
    /**
     * 查询实体的状态列表
     */
    @GetMapping("/list/{entityCode}")
    public Result<List<EntityStatus>> listByEntityCode(@PathVariable String entityCode) {
        List<EntityStatus> list = entityStatusService.findByEntityCode(entityCode);
        return Result.success(list);
    }
    
    /**
     * 根据分类查询
     */
    @GetMapping("/list/{entityCode}/{category}")
    public Result<List<EntityStatus>> listByCategory(@PathVariable String entityCode, @PathVariable String category) {
        List<EntityStatus> list = entityStatusService.findByCategory(entityCode, category);
        return Result.success(list);
    }
    
    /**
     * 保存实体状态
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody EntityStatus status) {
        entityStatusService.saveStatus(status);
        return Result.success();
    }
    
    /**
     * 批量保存实体状态
     */
    @PostMapping("/save-list/{entityCode}")
    public Result<Void> saveList(@PathVariable String entityCode, @RequestBody List<EntityStatus> statuses) {
        entityStatusService.saveStatusList(entityCode, statuses);
        return Result.success();
    }
    
    /**
     * 删除实体状态
     */
    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable String id) {
        entityStatusService.deleteStatus(id);
        return Result.success();
    }
}
