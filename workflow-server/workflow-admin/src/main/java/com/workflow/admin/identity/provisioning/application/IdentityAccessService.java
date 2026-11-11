package com.workflow.admin.identity.provisioning.application;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.identity.position.application.PositionOrganizationScopeService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.*;
import lombok.RequiredArgsConstructor;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import org.springframework.stereotype.Component;
import java.util.*;

/** 来源权限与资料对象范围统一校验，页面隐藏不代替服务端授权。 */
@Component
@RequiredArgsConstructor
public class IdentityAccessService {
    private final IdentityWriteProperties properties;
    private final IdentitySourceStore store;
    private final IdentityPersistenceMapper persistence;
    private final CurrentUserRoleService roles;
    private final PositionOrganizationScopeService scope;

    /**
     * 校验已登记来源及有效操作人，按来源配置决定是否校验来源写权限。
     * 无登录上下文时使用配置的服务账号，免来源权限模式下也以有效操作人作为审计归属。
     *
     * @param key 来源编码及稳定外部 ID，用于定位部署时登记的来源
     * @return 有效操作人与来源配置，供基础资料、扩展及审计共用
     * @throws IdentityWriteException 来源键非法、来源未登记、权限配置缺失、操作人无效或缺少必需权限
     */
    public Writer requireSource(ExternalIdentityKey key) {
        if (key == null || key.sourceCode() == null || !key.sourceCode().matches("[a-z][a-z0-9_-]{0,63}")
                || key.externalId() == null || key.externalId().isBlank() || key.externalId().length() > 255
                || key.externalId().indexOf(0) >= 0) fail("INVALID_MODEL", "key", "来源键非法");
        var config = properties.getSources().get(key.sourceCode());
        if (config == null || config.isPermissionCheckEnabled()
                && (config.getWritePermission() == null || config.getWritePermission().isBlank())) {
            fail("SOURCE_NOT_ALLOWED", "key.sourceCode", "来源未配置或没有写入授权");
        }
        String actor = UserContext.getUserId();
        if (actor == null) actor = config.getServiceUserId();
        requireActive(actor);
        // 来源免权限仅作用于此处，保留有效操作人供后续扩展组权限校验和必需审计使用。
        if (config.isPermissionCheckEnabled()) requirePermission(actor, config.getWritePermission());
        return new Writer(actor, config);
    }
    public void requirePermission(String actor, String permission) {
        if (!hasPermission(actor, permission)) fail("FORBIDDEN", "permission", "没有所需权限：" + permission);
    }
    public boolean hasPermission(String actor, String permission) {
        if (actor == null || permission == null || permission.isBlank()) return false;
        var permissions = PermissionUtil.getUserPermissions(actor);
        return permissions.contains("*") || permissions.contains(permission);
    }
    public String requireObject(IdentityObjectType target, String id) {
        var row = persistence.findObject(target, id);
        if (row == null) fail("OBJECT_NOT_FOUND", "id", "目标记录不存在");
        String actor = UserContext.getUserId();
        if (actor == null) {
            var binding = store.findLocal(target, id);
            if (binding == null) fail("FORBIDDEN", "actor", "未认证的资料访问");
            return requireSource(new ExternalIdentityKey((String) binding.get("source_code"), (String) binding.get("external_id"))).actorId();
        }
        requireActive(actor);
        if (!roles.isAdministrator()) {
            String unit = target == IdentityObjectType.ORGANIZATION ? id
                    : (String) (row.get("dept_id") == null ? row.get("org_id") : row.get("dept_id"));
            if (unit == null) fail("FORBIDDEN", "id", "目标没有可授权组织范围");
            scope.requireVisible(unit);
        }
        return actor;
    }
    private void requireActive(String actor) {
        if (actor == null || persistence.activeUser(actor) != 1) {
            fail("FORBIDDEN", "actor", "调用需要有效的登录用户或配置服务用户");
        }
    }
    private static void fail(String code, String field, String message) { throw new IdentityWriteException(code, field, message); }
    public record Writer(String actorId, IdentityWriteProperties.Source source) { }
}
