package com.workflow.process.assignment.api.web;

import com.workflow.contracts.identity.position.OrganizationBusinessLevelView;
import com.workflow.contracts.identity.position.OrganizationPositionDirectoryException;
import com.workflow.contracts.identity.port.OrganizationPositionDirectoryPort;
import com.workflow.contracts.identity.position.PositionDefinitionView;
import com.workflow.contracts.identity.resolver.PersonResolutionException;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.RequiresPermission;
import com.workflow.process.assignment.api.request.RelativePositionPreviewRequest;
import com.workflow.process.assignment.relative.RelativeOrgPositionPersonResolver;
import com.workflow.process.assignment.relative.RelativeOrgPositionPreview;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * 流程设计器的职务选项、业务层级与权威试算接口。
 */
@RestController
@RequestMapping("/api/process-design")
@RequiresPermission("process:definition:view")
public class RelativePositionProcessDesignController {

    private final OrganizationPositionDirectoryPort directoryPort;
    private final RelativeOrgPositionPersonResolver resolver;

    public RelativePositionProcessDesignController(
            OrganizationPositionDirectoryPort directoryPort,
            RelativeOrgPositionPersonResolver resolver) {
        this.directoryPort = directoryPort;
        this.resolver = resolver;
    }

    /**
     * 返回已启用职务；同时兼容前端传入 anchor 或直接传单位类型。
     */
    @GetMapping("/position-options")
    public ApiResponse<List<PositionDefinitionView>> positionOptions(
            @RequestParam(required = false) String applicableUnitType,
            @RequestParam(required = false) String anchor) {
        return ApiResponse.success(directoryPort.listEnabledPositions(
                normalizedUnitType(applicableUnitType, anchor)));
    }

    /** 返回可被 BUSINESS_LEVEL 模式引用的稳定业务层级。 */
    @GetMapping("/organization-business-levels")
    public ApiResponse<List<OrganizationBusinessLevelView>> businessLevels() {
        return ApiResponse.success(
                directoryPort.listEnabledOrganizationBusinessLevels());
    }

    /**
     * 捕获样例用户当前快照，并调用运行时同一 resolver 返回扫描轨迹。
     */
    @PostMapping("/relative-position-preview")
    public ApiResponse<RelativeOrgPositionPreview> preview(
            @RequestBody RelativePositionPreviewRequest request) {
        if (request == null || !StringUtils.hasText(request.sampleUserId())) {
            throw new IllegalArgumentException("sampleUserId 不能为空");
        }
        try {
            return ApiResponse.success(resolver.preview(
                    request.sampleUserId(), request.config()));
        } catch (PersonResolutionException exception) {
            return ApiResponse.success(failedPreview(
                    exception.reasonCode(),
                    exception.getMessage(),
                    exception.details().get("scannedUnits")));
        } catch (OrganizationPositionDirectoryException exception) {
            return ApiResponse.success(failedPreview(
                    exception.errorCode().name(),
                    exception.getMessage(),
                    null));
        }
    }

    private RelativeOrgPositionPreview failedPreview(
            String resultCode,
            String message,
            Object rawTrace) {
        return new RelativeOrgPositionPreview(
                null,
                failedTrace(rawTrace),
                null,
                List.of(),
                resultCode,
                resultCode,
                message,
                StringUtils.hasText(message) ? List.of(message) : List.of(),
                null);
    }

    private List<RelativeOrgPositionPreview.ScannedUnit> failedTrace(
            Object rawTrace) {
        if (!(rawTrace instanceof Iterable<?> values)) {
            return List.of();
        }
        java.util.ArrayList<RelativeOrgPositionPreview.ScannedUnit> result =
                new java.util.ArrayList<>();
        for (Object value : values) {
            String[] parts = String.valueOf(value).split(":", 3);
            if (parts.length != 3) {
                continue;
            }
            try {
                result.add(new RelativeOrgPositionPreview.ScannedUnit(
                        parts[1], null, null,
                        Integer.parseInt(parts[0]), parts[2]));
            } catch (NumberFormatException ignored) {
                // 旧版/外部错误轨迹无法安全解析时不伪造层级。
            }
        }
        return List.copyOf(result);
    }

    private String normalizedUnitType(
            String applicableUnitType,
            String anchor) {
        String raw = StringUtils.hasText(applicableUnitType)
                ? applicableUnitType : anchor;
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DEPARTMENT" -> "DEPT";
            case "ORGANIZATION" -> "ORG";
            case "DEPT", "ORG", "ANY" -> normalized;
            default -> throw new IllegalArgumentException(
                    "applicableUnitType/anchor 不支持: " + raw);
        };
    }
}
