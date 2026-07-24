package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.UiConfigDiffDTO;
import com.workflow.dto.UiConfigPublishRequest;
import com.workflow.entity.UiConfigRelease;
import com.workflow.service.UiConfigReleaseService;
import com.workflow.service.UiConfigurationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UI 配置发布管理控制器。
 * <p>统一管理表单（FORM）与列表（LIST）两类配置的发布生命周期：
 * 运行态发布获取、草稿快照、差异对比、发布、历史版本列表及激活指定版本。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UiConfigReleaseController {

    private final UiConfigReleaseService releaseService;
    private final UiConfigurationAccessService accessService;

    /**
     * 获取表单运行态发布快照（按 releaseId/version 或默认活跃版本）。GET /api/entity-forms/{id}/runtime-release
     *
     * @param id        表单ID
     * @param releaseId 指定发布ID（可选）
     * @param version   指定版本号（可选）
     * @return 运行态发布快照
     */
    @GetMapping("/entity-forms/{id}/runtime-release")
    public Result<Object> formRuntimeRelease(
            @PathVariable String id,
            @RequestParam(required = false) String releaseId,
            @RequestParam(required = false) Integer version) {
        return Result.success(
                releaseService.runtimeFormRelease(
                        id,
                        releaseId,
                        version));
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
     * 发布表单草稿为新版本。POST /api/entity-forms/{id}/publish
     *
     * @param id      表单ID
     * @param request 发布请求（可选描述）
     * @return 新建的发布记录
     */
    @PostMapping("/entity-forms/{id}/publish")
    public Result<UiConfigRelease> publishForm(
            @PathVariable String id,
            @RequestBody(required = false) UiConfigPublishRequest request) {
        accessService.requireFormAccess(id);
        return Result.success(releaseService.publish(
                UiConfigReleaseService.FORM,
                id,
                request == null ? null : request.getDescription()));
    }

    /**
     * 查询表单发布历史版本列表。GET /api/entity-forms/{id}/releases
     *
     * @param id 表单ID
     * @return 发布记录列表
     */
    @GetMapping("/entity-forms/{id}/releases")
    public Result<List<UiConfigRelease>> formReleases(@PathVariable String id) {
        accessService.requireFormAccess(id);
        return Result.success(
                releaseService.releases(UiConfigReleaseService.FORM, id));
    }

    /**
     * 激活表单的指定历史发布版本。POST /api/entity-forms/{id}/releases/{releaseId}/activate
     *
     * @param id        表单ID
     * @param releaseId 发布记录ID
     * @return 激活后的发布记录
     */
    @PostMapping("/entity-forms/{id}/releases/{releaseId}/activate")
    public Result<UiConfigRelease> activateForm(
            @PathVariable String id,
            @PathVariable String releaseId) {
        accessService.requireFormAccess(id);
        return Result.success(releaseService.activate(
                UiConfigReleaseService.FORM, id, releaseId));
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
     * 发布列表草稿为新版本。POST /api/entity-list-config/{id}/publish
     *
     * @param id      列表配置ID
     * @param request 发布请求（可选描述）
     * @return 新建的发布记录
     */
    @PostMapping("/entity-list-config/{id}/publish")
    public Result<UiConfigRelease> publishList(
            @PathVariable String id,
            @RequestBody(required = false) UiConfigPublishRequest request) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.publish(
                UiConfigReleaseService.LIST,
                id,
                request == null ? null : request.getDescription()));
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
     * 激活列表的指定历史发布版本。POST /api/entity-list-config/{id}/releases/{releaseId}/activate
     *
     * @param id        列表配置ID
     * @param releaseId 发布记录ID
     * @return 激活后的发布记录
     */
    @PostMapping("/entity-list-config/{id}/releases/{releaseId}/activate")
    public Result<UiConfigRelease> activateList(
            @PathVariable String id,
            @PathVariable String releaseId) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.activate(
                UiConfigReleaseService.LIST, id, releaseId));
    }
}
