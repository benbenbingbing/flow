package com.workflow.entity.ui.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;

import com.workflow.core.result.Result;
import com.workflow.core.result.PageResult;
import com.workflow.entity.ui.api.response.UiConfigDiffDTO;
import com.workflow.entity.ui.api.response.UiConfigActivationPreviewDTO;
import com.workflow.entity.ui.api.request.UiConfigReleaseOperationRequest;
import com.workflow.entity.ui.api.request.UiConfigDraftDiscardRequest;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.ui.api.response.UiConfigPublishPreviewDTO;
import com.workflow.entity.ui.api.response.UiConfigDraftDiscardResultDTO;
import com.workflow.entity.ui.api.response.UiConfigReleaseSummaryDTO;
import com.workflow.entity.ui.api.request.UiConfigPublishRequest;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * UI 配置发布管理控制器。
 * <p>统一管理表单（FORM）与列表（LIST）两类配置的发布生命周期：
 * 运行配置获取、草稿快照、差异对比与发布；历史版本列表及激活仅用于列表。
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UiConfigReleaseController {

    private final UiConfigReleaseService releaseService;
    private final UiConfigurationAccessService accessService;

    /**
     * 获取表单当前运行配置（旧坐标仅兼容摘要校验）。GET /api/entity-forms/{formId}/runtime-release
     *
     * @param formId    表单ID
     * @param releaseId 指定发布ID（可选）
     * @param version   指定版本号（可选）
     * @return 运行态发布快照
     */
    @GetMapping({"/entity-forms/{formId}/runtime", "/entity-forms/{formId}/runtime-release"})
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.FORM_RELEASE,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                    .FORM_RELEASE_PATH_QUERY)
    public Result<Object> formRuntimeRelease(
            @PathVariable String formId,
            @RequestParam(required = false) String runtimeContext) {
        return Result.success(releaseService.runtimeFormRelease(formId,
                runtimeContext == null || runtimeContext.isBlank() ? Map.of()
                        : new com.workflow.core.serialization.JsonDocumentCodec(new com.fasterxml.jackson.databind.ObjectMapper()).readObject(runtimeContext, "表单运行来源")));
    }

    /**
     * 获取表单草稿快照。GET /api/entity-forms/{id}/draft
     *
     * @param id 表单ID
     * @return 草稿快照
     */
    @GetMapping("/entity-forms/{id}/draft")
    public Result<Object> formDraft(@PathVariable String id) {
        accessService.requireFormAccess(id);
        return Result.success(
                releaseService.draftSnapshot(UiConfigReleaseService.FORM, id));
    }

    /**
     * 获取表单草稿与活跃发布的差异。GET /api/entity-forms/{id}/diff
     *
     * @param id 表单ID
     * @return 差异结构
     */
    @GetMapping("/entity-forms/{id}/diff")
    public Result<UiConfigDiffDTO> formDiff(@PathVariable String id) {
        accessService.requireFormAccess(id);
        return Result.success(releaseService.diff(UiConfigReleaseService.FORM, id));
    }

    /**
     * 撤销表单自身已保存但尚未发布的修改，外部依赖漂移保持不变。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于处理丢弃表单草稿
     * @return 处理后的丢弃表单草稿结果，供调用方继续处理
     */
    @PostMapping("/entity-forms/{id}/discard-draft")
    public Result<UiConfigDraftDiscardResultDTO> discardFormDraft(
            @PathVariable String id,
            @RequestBody UiConfigDraftDiscardRequest request) {
        accessService.requireFormAccess(id);
        return Result.success(releaseService.discardDraft(
                UiConfigReleaseService.FORM,
                id,
                request));
    }

    /**
     * 覆盖表单唯一当前配置，事务提交后全局生效。POST /api/entity-forms/{id}/publish
     *
     * @param id      表单ID
     * @param request 发布请求（可选描述）
     * @return 当前内容摘要和最近发布时间、发布人，不返回表单版本
     */
    @PostMapping("/entity-forms/{id}/publish")
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.PUBLISH,
            operation = "发布表单配置",
            risk = AuditRiskLevel.HIGH,
            targetType = "UI_CONFIG_RELEASE")
    public Result<Map<String, Object>> publishForm(
            @PathVariable String id,
            @RequestBody(required = false) UiConfigPublishRequest request) {
        accessService.requireFormAccess(id);
        UiConfigRelease current = releaseService.publish(UiConfigReleaseService.FORM, id, request);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("formId", id); result.put("contentHash", current.getContentHash());
        result.put("publishedBy", current.getPublishedBy()); result.put("publishedAt", current.getPublishedAt());
        return Result.success(result);
    }

    /**
     * 预检当前表单草稿及全局依赖，不创建表单版本。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于处理预览表单发布
     * @return 处理后的预览表单发布结果，供调用方继续处理
     */
    @PostMapping("/entity-forms/{id}/publish-preview")
    public Result<UiConfigPublishPreviewDTO> previewFormPublish(
            @PathVariable String id,
            @RequestBody(required = false) UiConfigPublishRequest request) {
        accessService.requireFormAccess(id);
        return Result.success(releaseService.publishPreview(
                UiConfigReleaseService.FORM,
                id,
                request));
    }

    /**
     * 获取列表草稿快照。GET /api/entity-list-config/{id}/draft
     *
     * @param id 列表配置ID
     * @return 草稿快照
     */
    @GetMapping("/entity-list-config/{id}/draft")
    public Result<Object> listDraft(@PathVariable String id) {
        accessService.requireListAccess(id);
        return Result.success(
                releaseService.draftSnapshot(UiConfigReleaseService.LIST, id));
    }

    /**
     * 获取列表草稿与活跃发布的差异。GET /api/entity-list-config/{id}/diff
     *
     * @param id 列表配置ID
     * @return 差异结构
     */
    @GetMapping("/entity-list-config/{id}/diff")
    public Result<UiConfigDiffDTO> listDiff(@PathVariable String id) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.diff(UiConfigReleaseService.LIST, id));
    }

    /**
     * 撤销列表自身已保存但尚未发布的修改，外部依赖漂移保持不变。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于处理丢弃列表草稿
     * @return 处理后的丢弃列表草稿结果，供调用方继续处理
     */
    @PostMapping("/entity-list-config/{id}/discard-draft")
    public Result<UiConfigDraftDiscardResultDTO> discardListDraft(
            @PathVariable String id,
            @RequestBody UiConfigDraftDiscardRequest request) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.discardDraft(
                UiConfigReleaseService.LIST,
                id,
                request));
    }

    /**
     * 发布列表草稿为新版本。POST /api/entity-list-config/{id}/publish
     *
     * @param id      列表配置ID
     * @param request 发布请求（可选描述）
     * @return 当前内容摘要和最近发布时间、发布人，不返回表单版本
     */
    @PostMapping("/entity-list-config/{id}/publish")
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.PUBLISH,
            operation = "发布列表配置",
            risk = AuditRiskLevel.HIGH,
            targetType = "UI_CONFIG_RELEASE")
    public Result<UiConfigRelease> publishList(
            @PathVariable String id,
            @RequestBody(required = false) UiConfigPublishRequest request) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.publish(
                UiConfigReleaseService.LIST,
                id,
                request));
    }

    /**
     * 预检列表普通发布的差异与风险。列表不支持新建热修复。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于处理预览列表发布
     * @return 处理后的预览列表发布结果，供调用方继续处理
     */
    @PostMapping("/entity-list-config/{id}/publish-preview")
    public Result<UiConfigPublishPreviewDTO> previewListPublish(
            @PathVariable String id,
            @RequestBody(required = false) UiConfigPublishRequest request) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.publishPreview(
                UiConfigReleaseService.LIST,
                id,
                request));
    }

    /**
     * 查询列表发布历史版本列表。GET /api/entity-list-config/{id}/releases
     *
     * @param id 列表配置ID
     * @return 发布记录列表
     */
    @GetMapping("/entity-list-config/{id}/releases")
    public Result<List<UiConfigRelease>> listReleases(@PathVariable String id) {
        accessService.requireListAccess(id);
        return Result.success(
                releaseService.releases(UiConfigReleaseService.LIST, id));
    }

    /**
     * 列出发布版本{@code summaries}；查询结果供调用方展示或继续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 符合条件的界面配置发布版本摘要结果，供调用方继续处理
     */
    @GetMapping("/entity-list-config/{id}/release-summaries")
    public Result<PageResult<UiConfigReleaseSummaryDTO>> listReleaseSummaries(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.releaseSummaries(
                UiConfigReleaseService.LIST,
                id,
                pageNum,
                pageSize));
    }

    /**
     * 激活列表的指定历史发布版本。POST /api/entity-list-config/{id}/releases/{releaseId}/activate
     *
     * @param id        列表配置ID
     * @param releaseId 发布记录ID
     * @param request 本次请求，后续经校验后用于激活界面配置发布版本列表
     * @return 激活后的发布记录
     */
    @PostMapping("/entity-list-config/{id}/releases/{releaseId}/activate")
    public Result<UiConfigRelease> activateList(
            @PathVariable String id,
            @PathVariable String releaseId,
            @RequestBody(required = false)
            UiConfigReleaseOperationRequest request) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.activate(
                UiConfigReleaseService.LIST,
                id,
                releaseId,
                request == null ? null : request.getReason(),
                request == null
                        ? null
                        : request.getExpectedActiveReleaseId()));
    }

    /**
     * 处理预览列表{@code activation}，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param releaseId 发布版本ID，后续用于处理预览列表{@code activation}时定位或关联目标
     * @return 处理后的预览列表{@code activation}结果，供调用方继续处理
     */
    @GetMapping(
            "/entity-list-config/{id}/releases/{releaseId}/activation-preview")
    public Result<UiConfigActivationPreviewDTO> previewListActivation(
            @PathVariable String id,
            @PathVariable String releaseId) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.activationPreview(
                UiConfigReleaseService.LIST,
                id,
                releaseId));
    }

    /**
     * 恢复列表草稿；结果供调用方的后续步骤使用。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param releaseId 发布版本ID，后续用于恢复列表草稿时定位或关联目标
     * @param request 本次请求，后续经校验后用于恢复列表草稿
     * @return 恢复后的列表草稿结果，供调用方继续处理
     */
    @PostMapping(
            "/entity-list-config/{id}/releases/{releaseId}/restore-draft")
    public Result<EntityListConfigDTO> restoreListDraft(
            @PathVariable String id,
            @PathVariable String releaseId,
            @RequestBody(required = false)
            UiConfigReleaseOperationRequest request) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.restoreListDraft(
                id,
                releaseId,
                request == null ? null : request.getReason()));
    }

}
