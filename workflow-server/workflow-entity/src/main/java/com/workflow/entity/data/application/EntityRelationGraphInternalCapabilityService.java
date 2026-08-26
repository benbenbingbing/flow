package com.workflow.entity.data.application;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan.InternalPurpose;
import org.springframework.stereotype.Service;

/**
 * 关系图内部用途的专用系统能力校验。
 *
 * <p>普通实体 VIEW/LIST 权限不足以启动流程协调、版本图或审计投影。这里
 * 使用固定权限码做第二道门禁；未配置权限码时只允许拥有通配权限的受控
 * 系统管理员调用，按 fail-closed 处理。</p>
 */
@Service
public class EntityRelationGraphInternalCapabilityService {

    /** 校验当前用户是否具有指定内部用途的专用能力。 */
    public void require(InternalPurpose purpose) {
        if (purpose == null) {
            throw new IllegalArgumentException("关系图内部授权用途不能为空");
        }
        String permission = switch (purpose) {
            case PROCESS_COORDINATION ->
                    "entity:relation-graph:process-coordinate";
            case VERSION_GRAPH ->
                    "entity:relation-graph:version-read";
            case AUDIT_PROJECTION ->
                    "entity:relation-graph:audit-read";
        };
        if (!PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("没有关系图内部能力: " + permission);
        }
    }
}
