package com.workflow.entity.form.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;

import com.workflow.core.result.Result;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.api.request.EntityFormCopyRequest;
import com.workflow.entity.form.api.request.EntityFormMetadataPatchRequest;
import com.workflow.entity.form.api.request.EntityFormSaveRequest;
import com.workflow.entity.form.api.request.FormUniquePrecheckRequest;
import com.workflow.entity.form.api.response.FormUniquePrecheckResponse;
import com.workflow.entity.form.api.response.EntityFormResponse;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.application.PublishedFormUniquePrecheckService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.application.UiConfigDraftMetadataService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 实体表单管理控制器
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/entity-form")
@RequiredArgsConstructor
public class EntityFormController {
    
    private final EntityFormService formService;
    private final UiConfigDraftMetadataService metadataService;
    private final UiConfigurationAccessService accessService;
    private final EntityActionCapabilityService actionCapabilityService;
    private final PublishedFormUniquePrecheckService uniquePrecheckService;
    
    /**
     * 查询所有表单列表
     *
     * @return 符合条件的实体表单结果，供调用方继续处理
     */
    @GetMapping("/list")
    public Result<List<EntityFormResponse>> list() {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(formService.list().stream()
                .map(EntityFormResponse::from).toList());
    }
    
    /**
     * 查询实体的表单列表
     *
     * @param entityId 实体ID，后续用于列出实体时定位或关联目标
     * @return 符合条件的实体表单结果，供调用方继续处理
     */
    @GetMapping("/entity/{entityId}")
    public Result<List<EntityFormResponse>> listByEntity(@PathVariable String entityId) {
        accessService.requireEntityFormAccess(entityId);
        return Result.success(formService.getFormsByEntityId(entityId).stream()
                .map(EntityFormResponse::from).toList());
    }
    
    /**
     * 根据ID查询表单
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的{@code result<entity}表单{@code response>}结果，供调用方继续处理
     */
    @GetMapping("/{id}")
    public Result<EntityFormResponse> getById(@PathVariable String id) {
        accessService.requireFormAccess(id);
        return Result.success(EntityFormResponse.from(formService.getById(id)));
    }
    
    /**
     * 新增表单
     *
     * @param form 表单，作为 {@code accessService.requireNewFormAccess} 的输入影响后续处理
     * @return 保存后的实体表单结果，供调用方继续处理
     */
    @PostMapping
    public Result<EntityFormResponse> save(@Validated @RequestBody EntityForm form) {
        if (StringUtils.hasText(form.getId())) {
            throw new IllegalArgumentException("新增表单不能携带 id");
        }
        accessService.requireNewFormAccess(form);
        return Result.success(EntityFormResponse.from(formService.saveForm(form)));
    }
    
    /**
     * 更新表单
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于更新实体表单
     * @return 更新后的实体表单结果，供调用方继续处理
     */
    @PostMapping("/{id}/update")
    public Result<EntityFormResponse> update(
            @PathVariable String id,
            @RequestBody EntityFormSaveRequest request) {
        accessService.requireFormAccess(id);
        return Result.success(EntityFormResponse.from(formService.saveForm(
                request.toEntity(id),
                request.getExpectedRevision())));
    }

    /**
     * 增量更新表单元数据。POST /api/entity-form/{id}/patch
     *
     * @param id      表单ID
     * @param request 元数据补丁请求
     * @return 更新后的表单
     */
    @PostMapping("/{id}/patch")
    public Result<EntityFormResponse> patch(
            @PathVariable String id,
            @RequestBody EntityFormMetadataPatchRequest request) {
        accessService.requireFormAccess(id);
        return Result.success(EntityFormResponse.from(metadataService.patchForm(id, request)));
    }
    
    /**
     * 删除表单
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 删除后的实体表单结果，供调用方继续处理
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable String id) {
        accessService.requireFormAccess(id);
        formService.deleteForm(id);
        return Result.success();
    }
    
    /**
     * 获取实体的字段列表。
     * 子表单运行态会按实体 ID 读取字段做回退渲染，不能要求表单设计管理权限。
     * 登录用户需具备该实体的设计查看权，或任一标准数据动作权限。
     *
     * @param entityId 实体ID，后续用于读取实体字段时定位或关联目标
     * @return 符合条件的实体字段结果，供调用方继续处理
     */
    @GetMapping("/entity/{entityId}/fields")
    public Result<List<EntityField>> getEntityFields(@PathVariable String entityId) {
        actionCapabilityService.requireEntityMetadataAccess(
                formService.requireEntityCode(entityId));
        return Result.success(formService.getEntityFields(entityId));
    }
    
    /**
     * 获取表单字段
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的实体表单字段结果，供调用方继续处理
     */
    @GetMapping("/{id}/fields")
    public Result<List<EntityFormField>> getFormFields(@PathVariable String id) {
        accessService.requireFormAccess(id);
        return Result.success(formService.getFormFields(id));
    }

    /**
     * 对当前用户获准使用的已发布表单字段执行唯一性提前检查。
     * 最终提交仍会在写事务内重新校验，调用方不能把 available 当作保存承诺。
     *
     * @param formId 表单ID，后续用于处理唯一预检查时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理唯一预检查
     * @return 处理后的唯一预检查结果，供调用方继续处理
     */
    @PostMapping("/{formId}/unique-precheck")
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.FORM_CONTEXT,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                    .FORM_UNIQUE_PRECHECK)
    public Result<FormUniquePrecheckResponse> uniquePrecheck(
            @PathVariable String formId,
            @RequestBody FormUniquePrecheckRequest request) {
        return Result.success(uniquePrecheckService.precheck(formId, request));
    }
    
    /**
     * 获取实体的默认表单
     *
     * @param entityId 实体ID，后续用于读取默认表单时定位或关联目标
     * @return 符合条件的{@code result<entity}表单{@code response>}结果，供调用方继续处理
     */
    @GetMapping("/entity/{entityId}/default")
    public Result<EntityFormResponse> getDefaultForm(@PathVariable String entityId) {
        accessService.requireEntityFormAccess(entityId);
        EntityForm form = formService.getDefaultForm(entityId);
        if (form == null) {
            return Result.success(null);
        }
        return Result.success(EntityFormResponse.from(form));
    }
    
    /**
     * 复制表单
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于复制表单
     * @return 复制后的表单结果，供调用方继续处理
     */
    @PostMapping("/{id}/copy")
    public Result<EntityFormResponse> copyForm(
            @PathVariable String id,
            @RequestBody(required = false) EntityFormCopyRequest request) {
        accessService.requireFormAccess(id);
        return Result.success(EntityFormResponse.from(formService.copyForm(
                id,
                request == null ? null : request.getFormName(),
                request == null ? null : request.getFormKey())));
    }
    
    /**
     * 设置默认表单
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 设置后的默认表单结果，供调用方继续处理
     */
    @PostMapping("/{id}/default")
    public Result<Void> setDefaultForm(@PathVariable String id) {
        accessService.requireFormAccess(id);
        formService.setDefaultForm(id);
        return Result.success();
    }
    
}
