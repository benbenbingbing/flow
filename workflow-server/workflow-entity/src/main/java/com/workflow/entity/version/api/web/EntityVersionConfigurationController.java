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

    /**
     * 混部期间保留旧客户端依赖的完整 List 响应，仅支持关键字过滤。
     *
     * @param keyword 关键字，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 符合条件的实体版本配置摘要结果，供调用方继续处理
     */
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

    /**
     * 根 GET 在 expand 版本保留旧草稿语义；新客户端必须使用 /current。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 处理后的旧版根草稿结果，供调用方继续处理
     */
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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 处理后的旧版草稿结果，供调用方继续处理
     */
    @Deprecated(forRemoval = true)
    @GetMapping("/{entityCode}/draft")
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<Map<String, Object>> legacyDraft(
            @PathVariable String entityCode) {
        return ApiResponse.success(service.legacyDraft(entityCode));
    }

    /**
     * 读取单例当前配置；独立路径避免混部时误读旧 Pod 的 root 草稿。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 处理后的当前结果，供调用方继续处理
     */
    @GetMapping("/{entityCode}/current")
    @RequiresPermission("entity:version:config:list")
    public ApiResponse<EntityVersionConfiguration> current(
            @PathVariable String entityCode) {
        return ApiResponse.success(service.get(entityCode));
    }

    /**
     * 根 POST 是本版过渡别名；新客户端使用对称的 /current 资源。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param ifMatch 条件匹配，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于保存实体版本配置
     * @return 保存后的实体版本配置结果，供调用方继续处理
     */
    @Deprecated(forRemoval = true)
    @PostMapping("/{entityCode}")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<EntityVersionConfiguration> save(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody EntityVersionConfiguration request) {
        return ApiResponse.success(service.save(
                entityCode, request, revision(ifMatch)));
    }

    /**
     * 校验、冻结并以 CAS 立即替换当前生效配置。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param ifMatch 条件匹配，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于保存实体版本配置当前
     * @return 保存后的实体版本配置当前结果，供调用方继续处理
     */
    @PostMapping("/{entityCode}/current")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<EntityVersionConfiguration> saveCurrent(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody EntityVersionConfiguration request) {
        return ApiResponse.success(service.save(
                entityCode, request, revision(ifMatch)));
    }

    /**
     * 旧草稿保存路由只推进 legacy 草稿，不提前改变当前运行配置。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param ifMatch 条件匹配，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于处理旧版保存草稿
     * @return 处理后的旧版保存草稿结果，供调用方继续处理
     */
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

    /**
     * 兼容更早的不带 If-Match 头保存路由，revision 仍必须由请求体提供。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param request 本次请求，后续经校验后用于处理旧版保存
     * @return 处理后的旧版保存结果，供调用方继续处理
     */
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

    /**
     * 旧发布动作会接管 pending 草稿；已同步时校验 revision 后幂等返回。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param ifMatch 条件匹配，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 处理后的旧版发布结果，供调用方继续处理
     */
    @Deprecated(forRemoval = true)
    @PostMapping("/{entityCode}/publish")
    @RequiresPermission("entity:version:config:publish")
    public ApiResponse<EntityVersionConfiguration> legacyPublish(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.success(service.publishLegacyDraft(
                entityCode, revision(ifMatch)));
    }

    /**
     * 兼容旧客户端以 POST releases 发布的动作，不再创建新的业务 release。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param ifMatch 条件匹配，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 处理后的旧版创建发布版本结果，供调用方继续处理
     */
    @Deprecated(forRemoval = true)
    @PostMapping("/{entityCode}/releases")
    @RequiresPermission("entity:version:config:publish")
    public ApiResponse<EntityVersionConfiguration> legacyCreateRelease(
            @PathVariable String entityCode,
            @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.success(service.publishLegacyDraft(
                entityCode, revision(ifMatch)));
    }

    /**
     * 兼容旧页面历史页签，只返回代表当前配置的一条过渡摘要。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 处理后的旧版{@code releases}结果，供调用方继续处理
     */
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

    /**
     * 校验实体版本配置；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param request 本次请求，后续经校验后用于校验实体版本配置
     * @return 校验后的实体版本配置结果，供调用方继续处理
     */
    @PostMapping("/{entityCode}/validate")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<EntityVersionValidationResult> validate(
            @PathVariable String entityCode,
            @RequestBody EntityVersionConfiguration request) {
        return ApiResponse.success(service.validate(entityCode, request));
    }

    /**
     * 处理作用域预览，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param body 请求体，后续用于处理作用域预览并传递处理结果
     * @return 处理后的作用域预览结果，供调用方继续处理
     */
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

    /**
     * 处理{@code simulate}，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param request 本次请求，后续经校验后用于处理{@code simulate}
     * @return 处理后的{@code simulate}结果，供调用方继续处理
     */
    @PostMapping("/{entityCode}/simulate")
    @RequiresPermission("entity:version:config:update")
    public ApiResponse<Map<String, Object>> simulate(
            @PathVariable String entityCode,
            @RequestBody EntityVersionSimulationRequest request) {
        return ApiResponse.success(
                matcher.simulate(entityCode, request));
    }

    /**
     * 处理修订版本，并将结果传给后续步骤。
     *
     * @param value 待处理修订版本的原始输入，结果供调用方继续使用
     * @return 处理后的修订版本结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Integer revision(String value) {
        if (value == null) {
            throw new IllegalArgumentException("If-Match不能为空");
        }
        String normalized = value.trim()
                .replace("W/", "")
                .replace("\"", "");
        return integer(normalized);
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 整理{@code without}预览键集合数据，供调用方遍历或继续处理。
     *
     * @param source 待处理{@code without}预览键集合的原始输入，结果供调用方继续使用
     * @return {@code without}预览键集合键值结果，供调用方继续处理
     */
    private Map<String, Object> withoutPreviewKeys(
            Map<String, Object> source) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(source);
        result.remove("recordId");
        result.remove("previewRecordId");
        return result;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }
}
