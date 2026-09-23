package com.workflow.process.task.api.web;

import com.workflow.admin.security.context.UserContext;
import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 不依赖当前任务办理权的实例级能力查询，供 PC 与移动端共用。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequiredArgsConstructor
public class ProcessInstanceOperationController {
    private final ProcessInstanceAccessService accessService;
    private final NodeOperationCapabilityService capabilityService;

    /**
     * 先校验实例可见性，再按与写接口相同的用户标识查询撤回权限。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 处理后的操作集合结果，供调用方继续处理
     */
    @GetMapping("/api/process-instance/{processInstanceId}/operations")
    public Result<Map<String, Boolean>> operations(@PathVariable String processInstanceId) {
        accessService.requireReadAccess(processInstanceId);
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) userId = UserContext.getUsername();
        return Result.success(Map.of("withdraw",
                capabilityService.canWithdrawProcess(processInstanceId, userId)));
    }
}
