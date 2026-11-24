package com.workflow.admin.identity.provisioning.application;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.identity.position.application.PositionOrganizationScopeService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.port.system.error.identity.IdentityWriteException;
import com.workflow.port.system.model.identity.*;
import lombok.RequiredArgsConstructor;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import org.springframework.stereotype.Component;
import java.util.*;

import static com.workflow.core.logging.LogValue.safe;

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
        validateKey(key);
        var config = properties.getSources().get(key.sourceCode());
        if (config == null || config.isPermissionCheckEnabled()
                && (config.getWritePermission() == null || config.getWritePermission().isBlank())) {
            fail("SOURCE_NOT_ALLOWED", "key.sourceCode", "同步来源「%s」未配置或未授权写入，请检查来源配置".formatted(safe(key.sourceCode())));
        }
        String actor = UserContext.getUserId();
        if (actor == null) actor = config.getServiceUserId();
        requireActive(actor);
        // 来源免权限仅作用于此处，保留有效操作人供后续扩展组权限校验和必需审计使用。
        if (config.isPermissionCheckEnabled()) requirePermission(actor, config.getWritePermission());
        return new Writer(actor, config);
    }

    /** 映射查询与写入共享来源键规则，避免同一外部 ID 在读写两端判定不同。 */
    static void validateKey(ExternalIdentityKey key) {
        if (key == null) fail("INVALID_MODEL", "key", "同步来源键（key）不能为空");
        if (key.sourceCode() == null || !key.sourceCode().matches("[a-z][a-z0-9_-]{0,63}")) {
            fail("INVALID_MODEL", "key", "来源编码（sourceCode）须以小写字母开头，最多 64 个字符，仅支持小写字母、数字、下划线和连字符");
        }
        if (key.externalId() == null || key.externalId().isBlank() || key.externalId().length() > 255 || key.externalId().indexOf(0) >= 0) {
            fail("INVALID_MODEL", "key", "外部 ID（externalId）不能为空白，最多 255 个字符，且不能包含 NUL 字符");
        }
    }
    public void requirePermission(String actor, String permission) {
        if (!hasPermission(actor, permission)) fail("FORBIDDEN", "permission", "没有所需权限：" + permission);
    }

    /** 修复会改变本地对象的来源归属，服务账号也须有管理权限，交互操作同时受现有组织范围约束。 */
    public void requireBindingChange(IdentityObjectType target, String id, Writer writer) {
        requirePermission(writer.actorId(), target == IdentityObjectType.USER ? "system:user:manage" : "system:organization:manage");
        if (UserContext.getUserId() != null) requireObject(target, id);
    }
    public boolean hasPermission(String actor, String permission) {
        if (actor == null || permission == null || permission.isBlank()) return false;
        var permissions = PermissionUtil.getUserPermissions(actor);
        return permissions.contains("*") || permissions.contains(permission);
    }
    public String requireObject(IdentityObjectType target, String id) {
        var row = persistence.findObject(target, id);
        if (row == null) fail("OBJECT_NOT_FOUND", "id", "localId「%s」对应的本地%s已删除或不存在"
                .formatted(safe(id), target == IdentityObjectType.USER ? "用户" : "组织"));
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
