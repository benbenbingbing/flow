package com.workflow.entity.data.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;

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
@EmbedDelegatedRuntimeApi(
        value = EmbedDelegatedRuntimeApi.Scope.REFERENCE_READ,
        targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.NONE)
@RestController
@RequestMapping("/api/entity-field-file-item")
@RequiredArgsConstructor
public class EntityFieldFileItemController {

    private final EntityFieldFileItemService fileItemService;

    /**
     * 根据字段ID查询附件项列表
     *
     * @param fieldId 字段ID，后续用于列出字段ID时定位或关联目标
     * @return 符合条件的实体字段文件条目结果，供调用方继续处理
     */
    @GetMapping("/field/{fieldId}")
    public Result<List<EntityFieldFileItem>> listByFieldId(@PathVariable String fieldId) {
        return Result.success(fileItemService.findByFieldId(fieldId));
    }
}
