package com.workflow.process.assignment.api.web;

import com.workflow.port.system.SysPositionPort;

import com.workflow.port.system.model.identity.position.OrganizationBusinessLevelView;
import com.workflow.port.system.error.identity.position.OrganizationPositionDirectoryException;
import com.workflow.port.system.SysOrganizationPort;
import com.workflow.port.system.model.identity.position.PositionDefinitionView;
import com.workflow.port.process.error.assignment.PersonResolutionException;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageRequest;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.RequiresPermission;
import com.workflow.process.assignment.api.request.RelativePositionPreviewRequest;
import com.workflow.process.assignment.extension.RelativeOrgPositionPersonResolver;
import com.workflow.process.assignment.application.model.RelativeOrgPositionPreview;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程设计器的职务选项、业务层级与权威试算接口。
 */
@RestController
@RequestMapping("/api/process-design")
@RequiresPermission("process:definition:view")
public class RelativePositionProcessDesignController {

    private final SysOrganizationPort organizations;
    private final SysPositionPort positions;
    private final RelativeOrgPositionPersonResolver resolver;

    /**
     * 初始化相对位置流程{@code design}控制器，保存构造参数供后续方法使用。
     *
     * @param organizations 读取组织状态和业务层级
     * @param positions 查询可用岗位与任职人
     * @param resolver 解析器依赖，保存到当前对象供后续业务方法调用
     */
    public RelativePositionProcessDesignController(
            SysOrganizationPort organizations,
            SysPositionPort positions,
            RelativeOrgPositionPersonResolver resolver) {
        this.organizations = organizations;
        this.positions = positions;
        this.resolver = resolver;
    }

    /** 返回全部启用职务，供组织和部门锚点共用。 */
    @GetMapping("/position-options")
    public ApiResponse<List<PositionDefinitionView>> positionOptions() {
        return ApiResponse.success(positions.listEnabledPositions());
    }

    /**
     * 在流程查看权限下搜索启用职务，返回选择器当前页。
     *
     * @param keyword 职务名称或编码
     * @param pageNum 从 1 开始的页码
     * @param pageSize 所需页大小，最多 100 条
     * @return 当前页职务定义与总数
     */
    @GetMapping("/position-options/page")
    public ApiResponse<PageResult<PositionDefinitionView>> positionOptionsPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageRequest page = PageRequest.normalize(pageNum, pageSize, 10, 100);
        SysPositionPort.PositionPage result = positions.pageEnabledPositions(
                keyword, page.pageNumber(), page.pageSize());
        return ApiResponse.success(new PageResult<>(
                result.records(), result.total(), page.pageNumber(), page.pageSize()));
    }

    /**
     * 按稳定编码读取已配置职务，用于首次打开节点时回显选择。
     *
     * @param codes 当前配置引用的职务编码
     * @return 已配置且仍启用的职务定义
     */
    @GetMapping("/position-options/batch")
    public ApiResponse<List<PositionDefinitionView>> positionOptionsBatch(@RequestParam List<String> codes) {
        return ApiResponse.success(positions.findEnabledPositionsByCodes(codes));
    }

    /**
     * 返回可被 BUSINESS_LEVEL 模式引用的稳定业务层级。
     *
     * @return 处理后的业务{@code levels}结果，供调用方继续处理
     */
    @GetMapping("/organization-business-levels")
    public ApiResponse<List<OrganizationBusinessLevelView>> businessLevels() {
        return ApiResponse.success(
                organizations.listEnabledOrganizationBusinessLevels());
    }

    /**
     * 捕获样例用户当前快照，并调用运行时同一 resolver 返回扫描轨迹。
     *
     * @param request 本次请求，后续经校验后用于处理预览
     * @return 处理后的预览结果，供调用方继续处理
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

    /**
     * 处理失败预览，并将结果传给后续步骤。
     *
     * @param resultCode 结果编码，后续用于处理失败预览时定位或关联目标
     * @param message 消息，供本方法处理失败预览时使用
     * @param rawTrace 原始追踪，作为 {@code RelativeOrgPositionPreview} 的输入影响后续处理
     * @return 处理后的失败预览结果，供调用方继续处理
     */
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

    /**
     * 整理失败追踪数据，供调用方遍历或继续处理。
     *
     * @param rawTrace 原始追踪，供本方法处理失败追踪时使用
     * @return 相对组织位置预览集合，供调用方遍历或展示
     */
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
}
