package com.workflow.embed.api.web;

import com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.Target;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort;
import com.workflow.core.web.CorrelationContext;
import com.workflow.embed.application.runtime.EmbedNativeFormTargetResolver;
import com.workflow.embed.domain.EmbedNativeFormTarget;
import com.workflow.embed.security.EmbedContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LIST 的 LOCAL_FORM 操作转换为 Flow 原生 Published Form 固定目标。
 *
 * <p>浏览器只声明 CREATE/VIEW 与 VIEW recordId；实体、表单和发布版本始终从
 * Session 固定 Release 恢复。VIEW 在返回前已执行映射用户对象权限和 DataScope。</p>
 */
@RestController
@RequestMapping("/api/embed/v1/runtime")
public class EmbedNativeFormTargetController {

    private final EmbedNativeFormTargetResolver targetResolver;
    private final EmbedNativeFormRuntimePort nativeRuntimePort;

    /**
     * 初始化嵌入式原生表单目标控制器，保存构造参数供后续方法使用。
     *
     * @param targetResolver 目标解析器依赖，保存到当前对象供后续业务方法调用
     * @param nativeRuntimePort 原生运行时端口依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedNativeFormTargetController(
            EmbedNativeFormTargetResolver targetResolver,
            EmbedNativeFormRuntimePort nativeRuntimePort) {
        this.targetResolver = targetResolver;
        this.nativeRuntimePort = nativeRuntimePort;
    }

    /**
     * 解析一次原生表单导航目标；不接受 formId/entityCode/releaseId。
     *
     * @param mode 模式标识，决定后续目标采用的处理分支
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param request 本次请求，后续经校验后用于处理目标
     * @return 处理后的目标结果，供调用方继续处理
     */
    @GetMapping("/native-form-target")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeViews.Target>> target(
            @RequestParam String mode,
            @RequestParam(required = false) String recordId,
            HttpServletRequest request) {
        EmbedNativeFormTarget fixed = targetResolver.authorize(
                EmbedContextHolder.require(), mode, recordId);
        String token = nativeRuntimePort.issueReleaseResolutionToken(
                new Target(
                        fixed.entityCode(), fixed.formId(),
                        fixed.formReleaseId(), fixed.formReleaseVersion(),
                        EmbedContextHolder.require().absoluteExpiresAt()));
        EmbedRuntimeViews.Target result = new EmbedRuntimeViews.Target(
                fixed.entityCode(), fixed.formId(),
                fixed.formReleaseId(), fixed.formReleaseVersion(),
                fixed.listKey(), fixed.listReleaseId(),
                fixed.listReleaseVersion(), fixed.entryMode(),
                fixed.recordId(), fixed.processInstanceId(), token, true, null,
                fixed.initialData(),
                fixed.parameters(), fixed.context());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EmbedApiEnvelope.ok(
                        result,
                        CorrelationContext.businessTraceId(request)));
    }
}
