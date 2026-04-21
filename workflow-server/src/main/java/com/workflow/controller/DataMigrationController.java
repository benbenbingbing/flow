package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.service.EntityDataMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据迁移控制器
 * 用于将旧表数据迁移到新表结构
 */
@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DataMigrationController {

    private final EntityDataMigrationService migrationService;

    /**
     * 迁移指定实体的数据
     * 
     * @param entityCode 实体编码
     * @return 迁移的数据条数
     */
    @PostMapping("/entity/{entityCode}")
    public ApiResponse<Integer> migrateEntity(@PathVariable String entityCode) {
        int count = migrationService.migrateEntityData(entityCode);
        return ApiResponse.success(count);
    }

    /**
     * 迁移所有实体的数据
     * 
     * @return 迁移统计信息
     */
    @PostMapping("/all")
    public ApiResponse<Map<String, Object>> migrateAll() {
        Map<String, Object> result = migrationService.migrateAll();
        return ApiResponse.success(result);
    }

    /**
     * 验证迁移结果
     * 
     * @param entityCode 实体编码
     * @return 验证结果
     */
    @GetMapping("/validate/{entityCode}")
    public ApiResponse<Map<String, Object>> validate(@PathVariable String entityCode) {
        Map<String, Object> result = migrationService.validateMigration(entityCode);
        return ApiResponse.success(result);
    }
}
