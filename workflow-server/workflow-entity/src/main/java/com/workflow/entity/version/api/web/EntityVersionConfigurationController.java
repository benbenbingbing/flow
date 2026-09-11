package com.workflow.entity.version.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.version.application.EntityVersionConfigurationService;
import com.workflow.entity.version.application.EntityVersionPolicyMatcher;
import com.workflow.entity.version.application.EntityVersionScopePreviewService;
import com.workflow.entity.version.application.model.EntityVersionConfigSummary;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.EntityVersionScopePreview;
import com.workflow.entity.version.application.model.EntityVersionValidationResult;
import com.workflow.entity.version.api.request.EntityVersionSimulationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;
import java.util.Map;

/**
 * 数据版本策略管理接口。
 */
@RestController
@RequestMapping("/api/entity-versions/configs")
@RequiredArgsConstructor
@RequiresPermission("entity:version:config:list")
public class EntityVersionConfigurationController {

    private final EntityVersionConfigurationService service;
    private final EntityVersionPolicyMatcher matcher;
    private final EntityVersionScopePreviewService previewService;
    private final ObjectMapper objectMapper;

    /** 混部期间保留旧客户端依赖的完整 List 响应，仅支持关键字过滤。 */
    @GetMapping
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<List<EntityVersionConfigSummary>> list(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(service.list(keyword));
    }

    /**
     * 分页查询实体数据版本配置摘要。
     *
     * @param keyword 实体名称或编码的模糊关键字
     * @param enabled 启用状态；false 表示未启用，包含已停用和未配置
     * @param pageNum 页码，非法下界会按统一分页规则归一为 1
     * @param pageSize 每页大小，统一限制为 1 到 100
     * @return 配置摘要分页结果
     */
    @GetMapping("/page")
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<PageResult<EntityVersionConfigSummary>> listPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.success(service.listPage(
                keyword, enabled, pageNum, pageSize));
    }

    /** 根 GET 在 expand 版本保留旧草稿语义；新客户端必须使用 /current。 */
    @Deprecated(forRemoval = true)
    @GetMapping("/{entityCode}")
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<Map<String, Object>> legacyRootDraft(
            @PathVariable String entityCode) {
        return ApiResponse.success(service.legacyDraft(entityCode));
    }

    /**
     * 滚动升级兼容旧前端的草稿读取。
     *
     * <p>N 版由应用查询兼容 active release，并保留兼容路由和写入桥。最终投影、
     * 对账有效 active release 后，N+1 改为 config-only 读取，可移除旧对外路由，
     * 但仍继续兼容写入；N+2 才停止兼容写入并在保留旧 schema 的前提下完成滚动。
     * 确认所有 N+1 Pod 和在途事务退出后，N+3 才能 contract 删除旧表、旧字段和
     * 发布权限。</p>
     */
    @Deprecated(forRemoval = true)
    @GetMapping("/{entityCode}/draft")
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<Map<String, Object>> legacyDraft(
            @PathVariable String entityCode) {
        return ApiResponse.success(service.legacyDraft(entityCode));
    }

    /** 读取单例当前配置；独立路径避免混部时误读旧 Pod 的 root 草稿。 */
    @GetMapping("/{entityCode}/current")
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<EntityVersionConfiguration> current(
            @PathVariable String entityCode) {
        return ApiResponse.success(service.get(entityCode));
    }

    /** 根 PUT 是本版过渡别名；新客户端使用对称的 /current 资源。 */
    @Deprecated(forRemoval = true)
    @PutMapping("/{entityCode}")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<EntityVersionConfiguration> save(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody EntityVersionConfiguration request) {
        return ApiResponse.success(service.save(
                entityCode, request, revision(ifMatch)));
    }

    /** 校验、冻结并以 CAS 立即替换当前生效配置。 */
    @PutMapping("/{entityCode}/current")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<EntityVersionConfiguration> saveCurrent(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody EntityVersionConfiguration request) {
        return ApiResponse.success(service.save(
                entityCode, request, revision(ifMatch)));
    }

    /** 旧草稿保存路由只推进 legacy 草稿，不提前改变当前运行配置。 */
    @Deprecated(forRemoval = true)
    @PostMapping("/{entityCode}/draft")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<Map<String, Object>> legacySaveDraft(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody EntityVersionConfiguration request) {
        return ApiResponse.success(service.saveLegacyDraft(
                entityCode, request, revision(ifMatch)));
    }

    /** 兼容更早的不带 If-Match 头保存路由，revision 仍必须由请求体提供。 */
    @Deprecated(forRemoval = true)
    @PostMapping("/{entityCode}/save")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<Map<String, Object>> legacySave(
            @PathVariable String entityCode,
            @RequestBody EntityVersionConfiguration request) {
        return ApiResponse.success(service.saveLegacyDraft(
                entityCode,
                request,
                request == null ? null : request.getRevision()));
    }

    /** 旧发布动作会接管 pending 草稿；已同步时校验 revision 后幂等返回。 */
    @Deprecated(forRemoval = true)
    @PostMapping("/{entityCode}/publish")
    @RequiresPermission("entity:version:config:publish")
    public ApiResponse<EntityVersionConfiguration> legacyPublish(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.success(service.publishLegacyDraft(
                entityCode, revision(ifMatch)));
    }

    /** 兼容旧客户端以 POST releases 发布的动作，不再创建新的业务 release。 */
    @Deprecated(forRemoval = true)
    @PostMapping("/{entityCode}/releases")
    @RequiresPermission("entity:version:config:publish")
    public ApiResponse<EntityVersionConfiguration> legacyCreateRelease(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.success(service.publishLegacyDraft(
                entityCode, revision(ifMatch)));
    }

    /** 兼容旧页面历史页签，只返回代表当前配置的一条过渡摘要。 */
    @Deprecated(forRemoval = true)
    @GetMapping("/{entityCode}/releases")
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<PageResult<Map<String, Object>>> legacyReleases(
            @PathVariable String entityCode,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.legacyReleasePage(
                entityCode, pageNum, pageSize));
    }

    @PostMapping("/{entityCode}/validate")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<EntityVersionValidationResult> validate(
            @PathVariable String entityCode,
            @RequestBody EntityVersionConfiguration request) {
        return ApiResponse.success(service.validate(entityCode, request));
    }

    @PostMapping("/{entityCode}/scope-preview")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<EntityVersionScopePreview> scopePreview(
            @PathVariable String entityCode,
            @RequestBody Map<String, Object> body) {
        Object draftValue = body.get("draft");
        EntityVersionConfiguration request = objectMapper.convertValue(
                draftValue == null ? withoutPreviewKeys(body) : draftValue,
                EntityVersionConfiguration.class);
        String recordId = text(body.get("recordId"));
        if (recordId == null) {
            recordId = text(body.get("previewRecordId"));
        }
        return ApiResponse.success(previewService.preview(
                entityCode, request, recordId));
    }

    @PostMapping("/{entityCode}/simulate")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<Map<String, Object>> simulate(
            @PathVariable String entityCode,
            @RequestBody EntityVersionSimulationRequest request) {
        return ApiResponse.success(
                matcher.simulate(entityCode, request));
    }

    private Integer revision(String value) {
        if (value == null) {
            throw new IllegalArgumentException("If-Match不能为空");
        }
        String normalized = value.trim()
                .replace("W/", "")
                .replace("\"", "");
        return integer(normalized);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("配置修订号格式错误");
        }
    }

    private Map<String, Object> withoutPreviewKeys(
            Map<String, Object> source) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(source);
        result.remove("recordId");
        result.remove("previewRecordId");
        return result;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }
}
