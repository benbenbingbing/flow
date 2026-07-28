package com.workflow.entity.definition.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityCodeRule;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实体编码规则管理Controller
 */
@AuthenticatedApi
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
