package com.workflow.admin.identity.provisioning.api.web;

import com.workflow.admin.identity.provisioning.application.IdentitySourceSnapshotService;
import com.workflow.contracts.identity.model.IdentityObjectType;
import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 原文仅通过专用管理接口按需读取；登录、权限和对象范围检查由服务执行。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/system/identity/source-snapshots")
@RequiredArgsConstructor
public class IdentitySourceSnapshotController {
    private final IdentitySourceSnapshotService snapshots;

    /** 返回单条最新原文及其是否仍对应当前业务资料，不在常规详情和列表中传输大文本。 */
    @GetMapping("/{target}/{localId}")
    public Result<IdentitySourceSnapshotService.View> read(@PathVariable IdentityObjectType target, @PathVariable String localId) {
        return Result.success(snapshots.read(target, localId));
    }
}
