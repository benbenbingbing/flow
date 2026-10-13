package com.workflow.entity.excel.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.entity.excel.config.EntityExcelConfigurationService;
import com.workflow.entity.excel.model.ExcelConfigurationModels.*;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 实体设计器的 Excel 配置接口，管理权限由服务统一检查。所有变更显式使用 POST。 */
@RestController
@AuthenticatedApi(objectAuthorization = true)
@RequestMapping("/api/entities/{entityCode}")
@RequiredArgsConstructor
public class EntityExcelConfigurationController {
    private final EntityExcelConfigurationService service;

    /** 返回当前实体全部方案及草稿/发布内容；不产生配置。 */
    @GetMapping("/excel-schemes")
    public ApiResponse<List<SchemeView>> list(@PathVariable String entityCode) {
        return ApiResponse.success(service.list(entityCode));
    }
    @GetMapping("/excel-schemes/{schemeCode}")
    public ApiResponse<SchemeView> get(@PathVariable String entityCode, @PathVariable String schemeCode) {
        return ApiResponse.success(service.get(entityCode, schemeCode));
    }
    @PostMapping("/excel-schemes/save")
    public ApiResponse<SchemeView> save(@PathVariable String entityCode, @RequestBody SaveScheme request) {
        return ApiResponse.success(service.save(entityCode, request));
    }
    /** 返回按已发布字段生成的可编辑草稿，由管理员补充表单后另行保存、发布。 */
    @PostMapping("/excel-schemes/generate")
    public ApiResponse<ExcelSchemeDocument> generate(@PathVariable String entityCode, @RequestBody GenerateRequest request) {
        return ApiResponse.success(service.generate(entityCode, request));
    }
    @PostMapping("/excel-schemes/{schemeCode}/publish")
    public ApiResponse<SchemeView> publish(@PathVariable String entityCode, @PathVariable String schemeCode, @RequestBody RevisionRequest request) {
        return ApiResponse.success(service.publish(entityCode, schemeCode, request.expectedRevision(), request.expectedSettingsRevision()));
    }
    @PostMapping("/excel-schemes/{schemeCode}/status")
    public ApiResponse<SchemeView> status(@PathVariable String entityCode, @PathVariable String schemeCode, @RequestBody StatusRequest request) {
        return ApiResponse.success(service.status(entityCode, schemeCode, request));
    }
    @PostMapping("/excel-schemes/{schemeCode}/delete")
    public ApiResponse<Void> delete(@PathVariable String entityCode, @PathVariable String schemeCode, @RequestBody RevisionRequest request) {
        service.delete(entityCode, schemeCode, request.expectedRevision());
        return ApiResponse.success();
    }
    @GetMapping("/excel-schemes/{schemeCode}/references")
    public ApiResponse<List<Reference>> references(@PathVariable String entityCode, @PathVariable String schemeCode) {
        return ApiResponse.success(service.references(entityCode, schemeCode));
    }
    @GetMapping("/excel-settings")
    public ApiResponse<SettingsView> settings(@PathVariable String entityCode) {
        return ApiResponse.success(service.settings(entityCode));
    }
    @PostMapping("/excel-settings/save")
    public ApiResponse<SettingsView> saveSettings(@PathVariable String entityCode, @RequestBody SaveSettings request) {
        return ApiResponse.success(service.saveSettings(entityCode, request));
    }
    @PostMapping("/excel-settings/publish")
    public ApiResponse<SettingsView> publishSettings(@PathVariable String entityCode, @RequestBody RevisionRequest request) {
        return ApiResponse.success(service.publishSettings(entityCode, request.expectedRevision()));
    }
}
