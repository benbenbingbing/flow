package com.workflow.entity.data.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;

import com.workflow.core.result.ApiResponse;
import com.workflow.entity.data.api.request.EntityBatchDeleteRequest;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.data.api.request.EntityDataExportRequest;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.EntityDataExportService;
import com.workflow.entity.form.application.EntityFormReleaseContext;
import com.workflow.entity.list.application.EntityDataListConfigService;
import com.workflow.entity.list.application.EntityListReleaseContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 实体数据控制器
 * 管理实体对应的数据（使用独立表结构）
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/entity-data")
@RequiredArgsConstructor
public class EntityDataController {
    
    private final EntityDataDynamicService entityDataDynamicService;
    private final EntityDataListConfigService entityDataListConfigService;
    private final EntityDataExportService entityDataExportService;
    private final EntityDataActionService entityDataActionService;
    private final com.workflow.entity.permission.application.EntityActionCapabilityService actionCapabilityService;
    
    /**
     * 获取某实体的所有数据（支持查询条件）
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param params 参数，作为 {@code hasPaging} 的输入影响后续处理
     * @return 符合条件的API{@code response<?>}结果，供调用方继续处理
     */
    @GetMapping("/entity/{entityCode}")
    public ApiResponse<?> listByEntity(
            @PathVariable String entityCode,
            @RequestParam(required = false) Map<String, String> params) {
        actionCapabilityService.requireStandardPermission(
                entityCode,
                com.workflow.entity.permission.application.EntityPermissionAction.LIST);
        boolean paged = hasPaging(params);
        long pageNum = positiveLong(params, "pageNum", "page", 1);
        long pageSize = positiveLong(params, "pageSize", "size", 10);
        if (params != null && !params.isEmpty()) {
            Map<String, Object> condition = new java.util.HashMap<>();
            params.forEach((k, v) -> {
                if (v != null && !v.trim().isEmpty()) {
                    condition.put(k, v);
                }
            });
            // 排除分页和排序等系统参数，避免被当作查询条件
            condition.remove("pageNum");
            condition.remove("pageSize");
            condition.remove("page");
            condition.remove("size");
            condition.remove("offset");
            condition.remove("limit");
            condition.remove("sort");
            condition.remove("orderBy");
            condition.remove("order");
            if (!condition.isEmpty()) {
                if (paged) {
                    return ApiResponse.success(
                            entityDataListConfigService.findPageWithConfig(
                                    entityCode,
                                    null,
                                    condition,
                                    pageNum,
                                    pageSize));
                }
                return ApiResponse.success(entityDataListConfigService.findListWithConfig(entityCode, null, condition));
            }
        }
        if (paged) {
            return ApiResponse.success(
                    entityDataListConfigService.findPageWithConfig(
                            entityCode,
                            null,
                            null,
                            pageNum,
                            pageSize));
        }
        return ApiResponse.success(entityDataListConfigService.findListWithConfig(entityCode, null, null));
    }

    /**
     * 获取某实体的数据列表（带列表配置扩展字段）
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param params 参数，作为 {@code hasPaging} 的输入影响后续处理
     * @return 符合条件的API{@code response<?>}结果，供调用方继续处理
     */
    @GetMapping("/entity/{entityCode}/list-with-config")
    public ApiResponse<?> listWithConfig(
            @PathVariable String entityCode,
            @RequestParam(required = false) String listKey,
            @RequestParam(required = false) Map<String, String> params) {
        Map<String, Object> condition = new java.util.HashMap<>();
        boolean paged = hasPaging(params);
        long pageNum = positiveLong(params, "pageNum", "page", 1);
        long pageSize = positiveLong(params, "pageSize", "size", 10);
        actionCapabilityService.requireStandardPermission(
                entityCode,
                com.workflow.entity.permission.application.EntityPermissionAction.LIST);
        if (params != null && !params.isEmpty()) {
            params.forEach((k, v) -> {
                if (v != null && !v.trim().isEmpty()) {
                    condition.put(k, v);
                }
            });
            // 排除系统参数
            condition.remove("listKey");
            condition.remove("pageNum");
            condition.remove("pageSize");
            condition.remove("page");
            condition.remove("size");
            condition.remove("offset");
            condition.remove("limit");
            condition.remove("sort");
            condition.remove("orderBy");
            condition.remove("order");
        }
        if (paged) {
            return ApiResponse.success(entityDataListConfigService.findPageWithConfig(
                    entityCode,
                    listKey,
                    condition.isEmpty() ? null : condition,
                    pageNum,
                    pageSize));
        }
        return ApiResponse.success(entityDataListConfigService.findListWithConfig(
                entityCode, listKey, condition.isEmpty() ? null : condition));
    }
    
    /**
     * 根据ID获取数据详情
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseId 发布版本ID，后续用于读取ID时定位或关联目标
     * @param releaseVersion 发布版本，供本方法读取ID时使用
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 符合条件的API{@code response<entity}数据{@code dto>}结果，供调用方继续处理
     */
    @GetMapping("/entity/{entityCode}/detail/{id}")
    public ApiResponse<EntityDataDTO> getById(
            @PathVariable String entityCode,
            @PathVariable String id,
            @RequestParam(required = false) String listKey,
            @RequestParam(required = false) String releaseId,
            @RequestParam(required = false) Integer releaseVersion,
            @RequestParam(required = false)
            String releaseResolutionToken) {
        return ApiResponse.success(entityDataActionService.getDetailReadOnly(
                entityCode,
                id,
                listKey,
                releaseContext(
                        releaseId,
                        releaseVersion,
                        releaseResolutionToken)));
    }

    /**
     * 加载详情并执行已发布的 DETAIL_LOAD 事件链。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param formId 表单ID，后续用于加载ID时定位或关联目标
     * @param releaseId 发布版本ID，后续用于加载ID时定位或关联目标
     * @param releaseVersion 发布版本，供本方法加载ID时使用
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @param formReleaseId 表单发布版本ID，后续用于加载ID时定位或关联目标
     * @param formReleaseVersion 表单发布版本，作为 {@code EntityFormReleaseContext} 的输入影响后续处理
     * @param formReleaseResolutionToken 表单发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 符合条件的API{@code response<entity}数据{@code dto>}结果，供调用方继续处理
     */
    @PostMapping("/entity/{entityCode}/detail/{recordId}/load")
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.RECORD_DETAIL,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                    .RECORD_DETAIL_PATH_QUERY)
    public ApiResponse<EntityDataDTO> loadById(
            @PathVariable String entityCode,
            @PathVariable String recordId,
            @RequestParam(required = false) String listKey,
            @RequestParam(required = false) String formId,
            @RequestParam(required = false) String releaseId,
            @RequestParam(required = false) Integer releaseVersion,
            @RequestParam(required = false)
            String releaseResolutionToken,
            @RequestParam(required = false) String formReleaseId,
            @RequestParam(required = false) Integer formReleaseVersion,
            @RequestParam(required = false)
            String formReleaseResolutionToken) {
        return ApiResponse.success(entityDataActionService.getDetail(
                entityCode,
                recordId,
                listKey,
                formId,
                releaseContext(
                        releaseId,
                        releaseVersion,
                        releaseResolutionToken),
                new EntityFormReleaseContext(
                        formReleaseId,
                        formReleaseVersion,
                        formReleaseResolutionToken)));
    }
    
    /**
     * 根据流程实例ID获取数据
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseId 发布版本ID，后续用于读取流程实例时定位或关联目标
     * @param releaseVersion 发布版本，供本方法读取流程实例时使用
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 符合条件的API{@code response<entity}数据{@code dto>}结果，供调用方继续处理
     */
    @GetMapping("/entity/{entityCode}/process/{processInstanceId}")
    public ApiResponse<EntityDataDTO> getByProcessInstance(
            @PathVariable String entityCode, 
            @PathVariable String processInstanceId,
            @RequestParam(required = false) String listKey,
            @RequestParam(required = false) String releaseId,
            @RequestParam(required = false) Integer releaseVersion,
            @RequestParam(required = false)
            String releaseResolutionToken) {
        actionCapabilityService.requireAnyStandardPermission(
                entityCode,
                com.workflow.entity.permission.application.EntityPermissionAction.VIEW,
                com.workflow.entity.permission.application.EntityPermissionAction.APPROVE);
        return ApiResponse.success(entityDataActionService.getDetailByProcessInstance(
                entityCode,
                processInstanceId,
                listKey,
                releaseContext(
                        releaseId,
                        releaseVersion,
                        releaseResolutionToken)));
    }
    
    /**
     * 保存数据
     * 如果DTO中startProcess为true且实体绑定了流程，则同时发起流程
     *
     * @param dto DTO，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 保存后的实体数据结果，供调用方继续处理
     */
    @PostMapping
    public ApiResponse<EntityDataDTO> save(@RequestBody EntityDataDTO dto) {
        return ApiResponse.success(entityDataActionService.create(
                dto,
                releaseContext(
                        dto.getListReleaseId(),
                        dto.getListReleaseVersion(),
                        dto.getListReleaseResolutionToken())));
    }
    
    /**
     * 更新数据
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseId 发布版本ID，后续用于更新实体数据时定位或关联目标
     * @param releaseVersion 发布版本，供本方法更新实体数据时使用
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @param formData 表单数据，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 更新后的实体数据结果，供调用方继续处理
     */
    @PostMapping("/entity/{entityCode}/detail/{id}/update")
    public ApiResponse<EntityDataDTO> update(
            @PathVariable String entityCode, 
            @PathVariable String id, 
            @RequestParam(required = false) String listKey,
            @RequestParam(required = false) String releaseId,
            @RequestParam(required = false) Integer releaseVersion,
            @RequestParam(required = false)
            String releaseResolutionToken,
            @RequestBody Map<String, Object> formData) {
        return ApiResponse.success(entityDataActionService.update(
                entityCode,
                id,
                listKey,
                formData,
                releaseContext(
                        releaseId,
                        releaseVersion,
                        releaseResolutionToken)));
    }
    
    /**
     * 删除数据
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseId 发布版本ID，后续用于删除实体数据时定位或关联目标
     * @param releaseVersion 发布版本，供本方法删除实体数据时使用
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 删除后的实体数据结果，供调用方继续处理
     */
    @PostMapping("/entity/{entityCode}/detail/{id}/delete")
    public ApiResponse<Void> delete(
            @PathVariable String entityCode,
            @PathVariable String id,
            @RequestParam(required = false) String listKey,
            @RequestParam(required = false) String releaseId,
            @RequestParam(required = false) Integer releaseVersion,
            @RequestParam(required = false)
            String releaseResolutionToken) {
        entityDataActionService.delete(
                entityCode,
                id,
                listKey,
                releaseContext(
                        releaseId,
                        releaseVersion,
                        releaseResolutionToken));
        return ApiResponse.success();
    }

    /**
     * 批量删除实体数据。POST /api/entity-data/entity/{entityCode}/batch-delete
     *
     * @param entityCode 实体编码
     * @param request   批量删除请求（含ID列表与列表标识）
     * @return 无数据返回
     */
    @PostMapping("/entity/{entityCode}/batch-delete")
    public ApiResponse<Void> batchDelete(
            @PathVariable String entityCode,
            @RequestBody EntityBatchDeleteRequest request) {
        entityDataActionService.batchDelete(
                entityCode,
                request.getIds(),
                request.getListKey(),
                releaseContext(
                        request.getReleaseId(),
                        request.getReleaseVersion(),
                        request.getReleaseResolutionToken()));
        return ApiResponse.success();
    }
    
    /**
     * 条件查询
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @return 检索后的实体数据结果，供调用方继续处理
     */
    @PostMapping("/entity/{entityCode}/search")
    public ApiResponse<List<EntityDataDTO>> search(
            @PathVariable String entityCode, 
            @RequestBody Map<String, Object> condition) {
        actionCapabilityService.requireStandardPermission(
                entityCode,
                com.workflow.entity.permission.application.EntityPermissionAction.LIST);
        return ApiResponse.success(entityDataListConfigService.findListWithConfig(entityCode, null, condition));
    }
    
    /**
     * 统计数量
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 统计后的实体数据结果，供调用方继续处理
     */
    @GetMapping("/entity/{entityCode}/count")
    public ApiResponse<Long> count(@PathVariable String entityCode) {
        actionCapabilityService.requireStandardPermission(
                entityCode,
                com.workflow.entity.permission.application.EntityPermissionAction.LIST);
        return ApiResponse.success(entityDataDynamicService.count(entityCode));
    }

    /**
     * 导出实体数据（选中或全部）
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param request 本次请求，后续经校验后用于处理导出
     * @param response 响应，供本方法处理导出时使用
     */
    @PostMapping("/entity/{entityCode}/export")
    public void export(
            @PathVariable String entityCode,
            @RequestBody EntityDataExportRequest request,
            HttpServletResponse response) {
        entityDataExportService.export(entityCode, request, response);
    }

    /**
     * 判断查询参数是否包含分页参数（pageNum/page/pageSize/size）。
     *
     * @param params 参数，供本方法判断是否具有{@code paging}时使用
     * @return {@code paging}条件成立时为 true，否则为 false
     */
    private boolean hasPaging(Map<String, String> params) {
        return params != null && (
                params.containsKey("pageNum")
                        || params.containsKey("page")
                        || params.containsKey("pageSize")
                        || params.containsKey("size"));
    }

    /**
     * 处理发布版本上下文，并将结果传给后续步骤。
     *
     * @param releaseId 发布版本ID，后续用于处理发布版本上下文时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code EntityListReleaseContext} 的输入影响后续处理
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 处理后的发布版本上下文结果，供调用方继续处理
     */
    private EntityListReleaseContext releaseContext(
            String releaseId,
            Integer releaseVersion,
            String releaseResolutionToken) {
        return new EntityListReleaseContext(
                releaseId,
                releaseVersion,
                releaseResolutionToken);
    }

    /**
     * 从参数中读取分页数值，优先取 primaryKey，缺失时取 fallbackKey，均为空时返回默认值。
     * 非法或非正整数抛 IllegalArgumentException。
     *
     * @param params 参数，供本方法处理正数{@code long}时使用
     * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
     * @param fallbackKey 兜底键，主值不可用时供后续处理兜底
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 处理后的正数{@code long}结果，供调用方继续处理
     */
    private long positiveLong(
            Map<String, String> params,
            String primaryKey,
            String fallbackKey,
            long defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        String value = params.get(primaryKey);
        if (value == null || value.isBlank()) {
            value = params.get(fallbackKey);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(primaryKey + " 必须是正整数");
        }
    }
}
