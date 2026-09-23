package com.workflow.entity.definition.api.web;

import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.Result;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityCodeRule;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实体编码规则管理Controller
 */
@RequiresPermission("entity:definition:manage")
@RestController
@RequestMapping("/api/entity-code-rule")
@RequiredArgsConstructor
public class EntityCodeRuleController {
    
    private final EntityCodeGeneratorService codeGeneratorService;
    
    /**
     * 获取实体的编码规则
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的{@code result<entity}编码{@code rule>}结果，供调用方继续处理
     */
    @GetMapping("/{entityCode}")
    public Result<EntityCodeRule> getRule(@PathVariable String entityCode) {
        EntityCodeRule rule = codeGeneratorService.getRule(entityCode);
        return Result.success(rule);
    }
    
    /**
     * 保存编码规则
     *
     * @param rule 规则，供本方法保存规则时使用
     * @return 保存后的规则结果，供调用方继续处理
     */
    @PostMapping
    public Result<Void> saveRule(@RequestBody EntityCodeRule rule) {
        codeGeneratorService.saveRule(rule);
        return Result.success();
    }
    
    /**
     * 预览编码
     *
     * @param rule 规则，供本方法处理预览编码时使用
     * @return 处理后的预览编码结果，供调用方继续处理
     */
    @PostMapping("/preview")
    public Result<String> previewCode(@RequestBody EntityCodeRule rule) {
        String preview = codeGeneratorService.previewCode(rule);
        return Result.success(preview);
    }
}
