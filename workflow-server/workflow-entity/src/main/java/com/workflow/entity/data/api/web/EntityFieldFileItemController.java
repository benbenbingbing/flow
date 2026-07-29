package com.workflow.entity.data.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import com.workflow.entity.data.application.EntityFieldFileItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体字段附件项配置控制器
 */
@AuthenticatedApi
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
