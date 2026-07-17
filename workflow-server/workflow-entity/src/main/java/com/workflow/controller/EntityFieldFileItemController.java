package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.EntityFieldFileItem;
import com.workflow.service.EntityFieldFileItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体字段附件项配置控制器
 */
@RestController
@RequestMapping("/api/entity-field-file-item")
@RequiredArgsConstructor
public class EntityFieldFileItemController {

    private final EntityFieldFileItemService fileItemService;

    /**
     * 根据字段ID查询附件项列表
     */
    @GetMapping("/field/{fieldId}")
    public Result<List<EntityFieldFileItem>> listByFieldId(@PathVariable String fieldId) {
        return Result.success(fileItemService.findByFieldId(fieldId));
    }
}
