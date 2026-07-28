package com.workflow.admin.audit.api;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
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

    @GetMapping
    public ApiResponse<PageResult<SystemOperationLog>> page(SystemAuditQuery query) {
        require("system:audit:list");
        return ApiResponse.success(queryService.page(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<SystemOperationLog> detail(@PathVariable String id) {
        require("system:audit:detail");
        return ApiResponse.success(queryService.getRequired(id));
    }

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

    private void append(StringBuilder csv, String value) {
        csv.append(csvValue(value)).append(',');
    }

    private void appendLast(StringBuilder csv, String value) {
        csv.append(csvValue(value)).append('\n');
    }

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

    private void require(String permission) {
        if (!PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("没有权限查看系统审计日志: " + permission);
        }
    }
}
