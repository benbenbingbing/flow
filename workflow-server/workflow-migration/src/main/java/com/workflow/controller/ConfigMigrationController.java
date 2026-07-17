package com.workflow.controller;

import com.workflow.common.ForbiddenException;
import com.workflow.common.PermissionUtil;
import com.workflow.dto.ApiResponse;
import com.workflow.dto.migration.ConfigEnvironmentMappingRequest;
import com.workflow.dto.migration.ConfigExportRequest;
import com.workflow.dto.migration.ConfigImportPublishRequest;
import com.workflow.dto.migration.ConfigMigrationAssetQuery;
import com.workflow.dto.migration.ConfigMigrationMarkRequest;
import com.workflow.entity.migration.ConfigImportItem;
import com.workflow.entity.migration.ConfigMigrationAsset;
import com.workflow.service.migration.ConfigMigrationAssetService;
import com.workflow.service.migration.ConfigMigrationImportApplyService;
import com.workflow.service.migration.ConfigMigrationPackageService;
import com.workflow.service.migration.DownloadFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config-migration")
@RequiredArgsConstructor
public class ConfigMigrationController {

    private final ConfigMigrationAssetService assetService;
    private final ConfigMigrationPackageService packageService;
    private final ConfigMigrationImportApplyService importApplyService;

    @GetMapping("/assets")
    public ApiResponse<List<ConfigMigrationAsset>> assets(ConfigMigrationAssetQuery query) {
        require("config-migration:list");
        return ApiResponse.success(assetService.query(query));
    }

    @GetMapping("/assets/{id}")
    public ApiResponse<ConfigMigrationAsset> asset(@PathVariable String id) {
        require("config-migration:list");
        return ApiResponse.success(assetService.getRequired(id));
    }

    @PutMapping("/assets/{id}/mark")
    public ApiResponse<ConfigMigrationAsset> mark(
            @PathVariable String id,
            @RequestBody ConfigMigrationMarkRequest request) {
        require("config-migration:export");
        return ApiResponse.success(assetService.updateMark(id, request));
    }

    @PostMapping("/packages/export")
    public ApiResponse<Map<String, Object>> export(@RequestBody ConfigExportRequest request) {
        require("config-migration:export");
        return ApiResponse.success(packageService.exportPackage(request));
    }

    @GetMapping("/packages")
    public ApiResponse<List<Map<String, Object>>> exportPackages() {
        require("config-migration:list");
        return ApiResponse.success(packageService.listExports());
    }

    @GetMapping("/packages/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        require("config-migration:download");
        DownloadFile file = packageService.downloadExport(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(file.data().length);
        return ResponseEntity.ok().headers(headers).body(file.data());
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> importPackage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String sourceEnvironment) {
        require("config-migration:import");
        return ApiResponse.success(packageService.importPackage(file, sourceEnvironment));
    }

    @GetMapping("/imports")
    public ApiResponse<List<Map<String, Object>>> imports() {
        require("config-migration:list");
        return ApiResponse.success(packageService.listImports());
    }

    @GetMapping("/imports/{id}/items")
    public ApiResponse<List<ConfigImportItem>> importItems(@PathVariable String id) {
        require("config-migration:list");
        return ApiResponse.success(packageService.listImportItems(id));
    }

    @PostMapping("/imports/{id}/analyze")
    public ApiResponse<Map<String, Object>> analyze(@PathVariable String id) {
        require("config-migration:analyze");
        return ApiResponse.success(packageService.analyze(id));
    }

    @PutMapping("/imports/{id}/mappings")
    public ApiResponse<Void> mappings(
            @PathVariable String id,
            @RequestBody ConfigEnvironmentMappingRequest request) {
        require("config-migration:analyze");
        packageService.saveMappings(id, request);
        return ApiResponse.success();
    }

    @GetMapping("/imports/{id}/compare")
    public ApiResponse<Map<String, Object>> compare(@PathVariable String id) {
        require("config-migration:list");
        return ApiResponse.success(packageService.compare(id));
    }

    @PostMapping("/imports/{id}/publish")
    public ApiResponse<Map<String, Object>> publish(
            @PathVariable String id,
            @RequestBody(required = false) ConfigImportPublishRequest request) {
        require("config-migration:publish");
        return ApiResponse.success(importApplyService.publish(id, request));
    }

    @PostMapping("/imports/{id}/rollback")
    public ApiResponse<Map<String, Object>> rollback(@PathVariable String id) {
        require("config-migration:rollback");
        return ApiResponse.success(importApplyService.rollback(id));
    }

    private void require(String permission) {
        if (!PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("没有权限执行配置迁移操作: " + permission);
        }
    }
}
