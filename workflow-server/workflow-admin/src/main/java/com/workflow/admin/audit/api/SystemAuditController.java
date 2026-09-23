package com.workflow.admin.audit.api;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.core.result.ApiResponse;
import com.workflow.admin.audit.application.SystemAuditQueryService;
import com.workflow.admin.audit.domain.SystemOperationLog;
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
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 系统审计日志只读接口。
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/system/audit-logs")
@RequiredArgsConstructor
public class SystemAuditController {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SystemAuditQueryService queryService;

    /**
     * 分页查询系统审计；查询结果供调用方展示或继续处理。
     *
     * @param query 查询，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 符合条件的系统操作日志结果，供调用方继续处理
     */
    @GetMapping
    public ApiResponse<PageResult<SystemOperationLog>> page(SystemAuditQuery query) {
        require("system:audit:list");
        return ApiResponse.success(queryService.page(query));
    }

    /**
     * 处理详情，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 处理后的详情结果，供调用方继续处理
     */
    @GetMapping("/{id}")
    public ApiResponse<SystemOperationLog> detail(@PathVariable String id) {
        require("system:audit:detail");
        return ApiResponse.success(queryService.getRequired(id));
    }

    /**
     * 查询跨实体、流程、动作和配置发布的统一时间线投影。
     *
     * @param query 查询，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 处理后的统一结果，供调用方继续处理
     */
    @GetMapping("/unified")
    public ApiResponse<PageResult<UnifiedAuditEventView>> unified(
            UnifiedAuditQuery query) {
        require("system:audit:list");
        return ApiResponse.success(queryService.unifiedPage(query));
    }

    /**
     * 查询一次业务操作内的全部已接入事件，最多返回 500 条。
     *
     * @param operationId 操作ID，后续用于处理操作{@code timeline}时定位或关联目标
     * @return 处理后的操作{@code timeline}结果，供调用方继续处理
     */
    @GetMapping("/unified/operations/{operationId}")
    public ApiResponse<List<UnifiedAuditEventView>> operationTimeline(
            @PathVariable String operationId) {
        require("system:audit:detail");
        return ApiResponse.success(
                queryService.operationTimeline(operationId));
    }

    /**
     * 处理导出，并将结果传给后续步骤。
     *
     * @param query 查询，供本方法处理导出时使用
     * @return 处理后的导出结果，供调用方继续处理
     */
    @PostMapping("/export")
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.EXPORT,
            operation = "导出系统审计日志",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYSTEM_AUDIT_LOG",
            captureArguments = true)
    public ResponseEntity<byte[]> export(@RequestBody(required = false) SystemAuditQuery query) {
        require("system:audit:export");
        List<SystemOperationLog> records =
                queryService.export(query == null ? new SystemAuditQuery() : query);
        byte[] csv = toCsv(records).getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("system-audit-logs.csv", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(csv.length);
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    /**
     * 转换为CSV；输出作为后续校验或处理的输入。
     *
     * @param records 记录集合，供本方法转换为CSV时使用
     * @return 转换为后的CSV文本，供调用方比较或展示
     */
    private String toCsv(List<SystemOperationLog> records) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("时间,模块,操作,风险,结果,操作人,目标类型,目标ID,摘要,Trace ID,错误信息\n");
        for (SystemOperationLog record : records) {
            append(csv, record.getCreateTime() == null
                    ? null : TIME_FORMATTER.format(record.getCreateTime()));
            append(csv, record.getModuleCode());
            append(csv, record.getOperationName());
            append(csv, record.getRiskLevel());
            append(csv, record.getResult());
            append(csv, record.getOperatorName());
            append(csv, record.getTargetType());
            append(csv, record.getTargetId());
            append(csv, record.getSummary());
            append(csv, record.getTraceId());
            appendLast(csv, record.getErrorMessage());
        }
        return csv.toString();
    }

    /**
     * 追加系统审计；结果供后续流程传递或持久化。
     *
     * @param csv CSV，供本方法追加系统审计时使用
     * @param value 待追加系统审计的原始输入，结果供调用方继续使用
     */
    private void append(StringBuilder csv, String value) {
        csv.append(csvValue(value)).append(',');
    }

    /**
     * 追加最后；结果供后续流程传递或持久化。
     *
     * @param csv CSV，供本方法追加最后时使用
     * @param value 待追加最后的原始输入，结果供调用方继续使用
     */
    private void appendLast(StringBuilder csv, String value) {
        csv.append(csvValue(value)).append('\n');
    }

    /**
     * 生成CSV值文本，供后续匹配或展示。
     *
     * @param value 待处理CSV值的原始输入，结果供调用方继续使用
     * @return 处理后的CSV值文本，供调用方比较或展示
     */
    private String csvValue(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    /**
     * 校验并获取系统审计；不满足约束时阻止后续处理。
     *
     * @param permission 数据访问权限，后续与查询条件合并以限制可见记录
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private void require(String permission) {
        if (!PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("没有权限查看系统审计日志: " + permission);
        }
    }
}
