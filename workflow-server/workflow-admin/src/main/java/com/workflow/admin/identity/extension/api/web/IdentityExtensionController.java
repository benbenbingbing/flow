package com.workflow.admin.identity.extension.api.web;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.identity.extension.application.IdentityExtensionService;
import com.workflow.admin.identity.provisioning.application.IdentitySourceStore;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.model.*;
import com.workflow.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 管理页面元数据及详情；只允许注册扩展编码，具体资料还需对象级授权。 */
@com.workflow.core.security.AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/system/identity/extensions")
@RequiredArgsConstructor
public class IdentityExtensionController {
    private final IdentityExtensionService extensions;
    private final IdentitySourceStore sources;

    @GetMapping("/schema")
    public Result<List<IdentityExtensionSchema>> schema(@RequestParam IdentityObjectType targetType) {
        requireView(targetType);
        return Result.success(extensions.schemas(targetType, UserContext.getUserId()));
    }
    @GetMapping("/{target}/{id}")
    public Result<Map<String, Object>> detail(@PathVariable IdentityObjectType target, @PathVariable String id) {
        requireView(target);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("extensions", extensions.readAll(target, id));
        result.put("sourceInfo", sources.info(target, id));
        return Result.success(result);
    }
    private void requireView(IdentityObjectType target) {
        PermissionUtil.checkPermission(target == IdentityObjectType.USER ? "system:user:view" : "system:organization:view");
    }
}
