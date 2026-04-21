package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.EntityCodeRule;
import com.workflow.service.EntityCodeGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实体编码规则管理Controller
 */
@RestController
@RequestMapping("/api/entity-code-rule")
@RequiredArgsConstructor
public class EntityCodeRuleController {
    
    private final EntityCodeGeneratorService codeGeneratorService;
    
    /**
     * 获取实体的编码规则
     */
    @GetMapping("/{entityCode}")
    public Result<EntityCodeRule> getRule(@PathVariable String entityCode) {
        EntityCodeRule rule = codeGeneratorService.getRule(entityCode);
        return Result.success(rule);
    }
    
    /**
     * 保存编码规则
     */
    @PostMapping
    public Result<Void> saveRule(@RequestBody EntityCodeRule rule) {
        codeGeneratorService.saveRule(rule);
        return Result.success();
    }
    
    /**
     * 预览编码
     */
    @PostMapping("/preview")
    public Result<String> previewCode(@RequestBody EntityCodeRule rule) {
        String preview = codeGeneratorService.previewCode(rule);
        return Result.success(preview);
    }
}
