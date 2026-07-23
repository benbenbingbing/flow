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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 配置迁移 REST 接口。
 *
 * <p>提供配置迁移资产查询/标记、导出包生成与下载、导入批次上传/分析/比较/发布/回滚等 HTTP 接口，
 * 统一在入口处进行权限校验。</p>
 */
@RestController
@RequestMapping("/api/config-migration")
@RequiredArgsConstructor
public class ConfigMigrationController {

    private final ConfigMigrationAssetService assetService;
    private final ConfigMigrationPackageService packageService;
    private final ConfigMigrationImportApplyService importApplyService;

    /**
     * 分页/条件查询迁移资产列表。
     *
     * @param query 过滤条件(可选)
     * @return 资产列表
     */
    @GetMapping("/assets")
    public ApiResponse<List<ConfigMigrationAsset>> assets(ConfigMigrationAssetQuery query) {
        require("config-migration:list");
        return ApiResponse.success(assetService.query(query));
    }

    /**
     * 根据ID获取单个迁移资产详情。
     *
     * @param id 资产ID
     * @return 迁移资产
     */
    @GetMapping("/assets/{id}")
    public ApiResponse<ConfigMigrationAsset> asset(@PathVariable String id) {
        require("config-migration:list");
        return ApiResponse.success(assetService.getRequired(id));
    }

    /**
     * 更新迁移资产的待导出标记或迁移标签。
     *
     * @param id      资产ID
     * @param request 标记请求
     * @return 更新后的迁移资产
     */
    @PostMapping("/assets/{id}/mark")
    public ApiResponse<ConfigMigrationAsset> mark(
            @PathVariable String id,
            @RequestBody ConfigMigrationMarkRequest request) {
        require("config-migration:export");
        return ApiResponse.success(assetService.updateMark(id, request));
    }

    /**
     * 生成配置导出包。
     *
     * @param request 导出请求(资产ID、迁移标签、选择配置)
     * @return 导出包摘要信息
     */
    @PostMapping("/packages/export")
    public ApiResponse<Map<String, Object>> export(@RequestBody ConfigExportRequest request) {
        require("config-migration:export");
        return ApiResponse.success(packageService.exportPackage(request));
    }

    /**
     * 查询导出包列表。
     *
     * @return 导出包摘要列表
     */
    @GetMapping("/packages")
    public ApiResponse<List<Map<String, Object>>> exportPackages() {
        require("config-migration:list");
        return ApiResponse.success(packageService.listExports());
    }

    /**
     * 下载指定导出包的二进制文件。
     *
     * @param id 导出包ID
     * @return 文件流响应
     */
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

    /**
     * 上传并导入 wfpack 发布包。
     *
     * @param file              发布包文件
     * @param sourceEnvironment 源环境名称(可选，覆盖包内信息)
     * @return 导入批次摘要
     */
    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> importPackage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String sourceEnvironment) {
        require("config-migration:import");
        return ApiResponse.success(packageService.importPackage(file, sourceEnvironment));
    }

    /**
     * 查询导入批次列表。
     *
     * @return 导入批次摘要列表
     */
    @GetMapping("/imports")
    public ApiResponse<List<Map<String, Object>>> imports() {
        require("config-migration:list");
        return ApiResponse.success(packageService.listImports());
    }

    /**
     * 查询指定导入批次的条目列表。
     *
     * @param id 导入批次ID
     * @return 导入条目列表
     */
    @GetMapping("/imports/{id}/items")
    public ApiResponse<List<ConfigImportItem>> importItems(@PathVariable String id) {
        require("config-migration:list");
        return ApiResponse.success(packageService.listImportItems(id));
    }

    /**
     * 对导入批次执行分析(冲突比较、依赖解析、风险识别)。
     *
     * @param id 导入批次ID
     * @return 校验报告
     */
    @PostMapping("/imports/{id}/analyze")
    public ApiResponse<Map<String, Object>> analyze(@PathVariable String id) {
        require("config-migration:analyze");
        return ApiResponse.success(packageService.analyze(id));
    }

    /**
     * 保存环境映射并在保存后重新分析导入批次。
     *
     * @param id      导入批次ID
     * @param request 环境映射保存请求
     * @return 操作结果
     */
    @PostMapping("/imports/{id}/mappings")
    public ApiResponse<Void> mappings(
            @PathVariable String id,
            @RequestBody ConfigEnvironmentMappingRequest request) {
        require("config-migration:analyze");
        packageService.saveMappings(id, request);
        return ApiResponse.success();
    }

    /**
     * 查询导入批次的比较结果(包含批次摘要、条目与校验报告)。
     *
     * @param id 导入批次ID
     * @return 比较结果
     */
    @GetMapping("/imports/{id}/compare")
    public ApiResponse<Map<String, Object>> compare(@PathVariable String id) {
        require("config-migration:list");
        return ApiResponse.success(packageService.compare(id));
    }

    /**
     * 发布导入批次(应用资产配置到目标环境)。
     *
     * @param id      导入批次ID
     * @param request 发布请求(可选指定条目)
     * @return 发布结果
     */
    @PostMapping("/imports/{id}/publish")
    public ApiResponse<Map<String, Object>> publish(
            @PathVariable String id,
            @RequestBody(required = false) ConfigImportPublishRequest request) {
        require("config-migration:publish");
        return ApiResponse.success(importApplyService.publish(id, request));
    }

    /**
     * 回滚已发布的导入批次(恢复到发布前状态)。
     *
     * @param id 导入批次ID
     * @return 回滚结果
     */
    @PostMapping("/imports/{id}/rollback")
    public ApiResponse<Map<String, Object>> rollback(@PathVariable String id) {
        require("config-migration:rollback");
        return ApiResponse.success(importApplyService.rollback(id));
    }

    /**
     * 校验当前用户是否拥有指定权限，无权限抛出 ForbiddenException。
     *
     * @param permission 权限编码
     */
    private void require(String permission) {
        if (!PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("没有权限执行配置迁移操作: " + permission);
        }
    }
}
