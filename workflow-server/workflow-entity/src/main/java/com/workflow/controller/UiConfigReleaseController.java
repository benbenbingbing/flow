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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UiConfigReleaseController {

    private final UiConfigReleaseService releaseService;
    private final UiConfigurationAccessService accessService;

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

    @GetMapping("/entity-forms/{id}/draft")
    public Result<Object> formDraft(@PathVariable String id) {
        accessService.requireFormAccess(id);
        return Result.success(
                releaseService.draftSnapshot(UiConfigReleaseService.FORM, id));
    }

    @GetMapping("/entity-forms/{id}/diff")
    public Result<UiConfigDiffDTO> formDiff(@PathVariable String id) {
        accessService.requireFormAccess(id);
        return Result.success(releaseService.diff(UiConfigReleaseService.FORM, id));
    }

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

    @GetMapping("/entity-forms/{id}/releases")
    public Result<List<UiConfigRelease>> formReleases(@PathVariable String id) {
        accessService.requireFormAccess(id);
        return Result.success(
                releaseService.releases(UiConfigReleaseService.FORM, id));
    }

    @PostMapping("/entity-forms/{id}/releases/{releaseId}/activate")
    public Result<UiConfigRelease> activateForm(
            @PathVariable String id,
            @PathVariable String releaseId) {
        accessService.requireFormAccess(id);
        return Result.success(releaseService.activate(
                UiConfigReleaseService.FORM, id, releaseId));
    }

    @GetMapping("/entity-list-config/{id}/draft")
    public Result<Object> listDraft(@PathVariable String id) {
        accessService.requireListAccess(id);
        return Result.success(
                releaseService.draftSnapshot(UiConfigReleaseService.LIST, id));
    }

    @GetMapping("/entity-list-config/{id}/diff")
    public Result<UiConfigDiffDTO> listDiff(@PathVariable String id) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.diff(UiConfigReleaseService.LIST, id));
    }

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

    @GetMapping("/entity-list-config/{id}/releases")
    public Result<List<UiConfigRelease>> listReleases(@PathVariable String id) {
        accessService.requireListAccess(id);
        return Result.success(
                releaseService.releases(UiConfigReleaseService.LIST, id));
    }

    @PostMapping("/entity-list-config/{id}/releases/{releaseId}/activate")
    public Result<UiConfigRelease> activateList(
            @PathVariable String id,
            @PathVariable String releaseId) {
        accessService.requireListAccess(id);
        return Result.success(releaseService.activate(
                UiConfigReleaseService.LIST, id, releaseId));
    }
}
