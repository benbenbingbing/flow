package com.workflow.entity.data.application;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan.Grant;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan.AccessMode;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan.InternalPurpose;
import com.workflow.entity.definition.application.PublishedRelationPathResolver;
import com.workflow.entity.definition.application.model.PublishedRelationPath;
import com.workflow.entity.permission.api.response.DataPermissionResult;
import com.workflow.entity.permission.application.DataPermissionEngine;
import com.workflow.entity.permission.application.DataPermissionEngine.ExplicitListPermission;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 为关系图读取签发请求内、用户绑定的显式内部授权计划。
 *
 * <p>本服务先验证钉定路径，再逐实体检查 VIEW/LIST 能力并由数据权限引擎
 * 计算范围。调用方只能选择固定的内部用途，不能提交 SQL、范围条件或伪造
 * {@code listKey=null} 以绕过发布列表授权。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityRelationGraphAuthorizationService {

    /** 固定平台入口，不接受调用方或客户端提供 listKey。 */
    static final String INTERNAL_SCOPE_KEY = "__relation_graph_internal__";

    private final PublishedRelationPathResolver pathResolver;
    private final EntityActionCapabilityService actionCapabilityService;
    private final DataPermissionEngine dataPermissionEngine;
    private final SysUserService userService;
    private final EntityRelationGraphInternalCapabilityService
            internalCapabilityService;

    /**
     * 为平台内部场景签发授权。授权只在当前登录用户上下文中使用。
     *
     * @param path    精确发布关系路径
     * @param purpose 固定内部用途，不能为空
     * @return 不可由外部模块构造的服务端授权凭证
     */
    @Transactional(readOnly = true)
    public EntityRelationGraphAuthorizationPlan authorizeInternal(
            PublishedRelationPath path,
            InternalPurpose purpose) {
        if (purpose == null) {
            throw new IllegalArgumentException("关系图内部授权用途不能为空");
        }
        PublishedRelationPath verified = pathResolver.validate(path);
        SysUser user = currentUser();
        internalCapabilityService.require(purpose);
        Grant source = grant(
                0,
                verified.sourceEntityCode(),
                verified.sourceHistoryId(),
                verified.sourceSchemaHash(),
                user);
        List<Grant> hops = new ArrayList<>();
        for (PublishedRelationPath.Hop hop : verified.hops()) {
            hops.add(grant(
                    hop.index(),
                    hop.targetEntityCode(),
                    hop.targetHistoryId(),
                    hop.targetSchemaHash(),
                    user));
        }
        return new IssuedEntityRelationGraphAuthorizationPlan(
                user.getId(), purpose, source, hops);
    }

    private Grant grant(
            int hopIndex,
            String entityCode,
            String historyId,
            String schemaHash,
            SysUser user) {
        actionCapabilityService.requireAnyStandardPermission(
                entityCode,
                EntityPermissionAction.VIEW,
                EntityPermissionAction.LIST);
        // INTERNAL_SAFE 使用固定平台入口键，绝不传 null，也不接收调用方的
        // listKey。尚未为该入口发布范围时，权限引擎按安全默认拒绝。
        ExplicitListPermission calculation = dataPermissionEngine
                .calculateExplicitListPermission(
                        entityCode, INTERNAL_SCOPE_KEY, user);
        DataPermissionResult permission = calculation == null
                ? null : calculation.permission();
        if (permission == null) {
            throw new IllegalStateException("数据权限引擎未返回关系图授权计划");
        }
        String sql = permission.isHasPermission()
                ? (permission.isNeedFilter()
                        ? permission.getSqlCondition() : "1=1")
                : "1=0";
        if (!StringUtils.hasText(sql)) {
            throw new IllegalStateException("关系图数据权限计划缺少范围条件");
        }
        boolean explicitBypass = "BYPASS".equalsIgnoreCase(
                permission.getDataScopeMode());
        if (permission.isHasPermission()
                && !explicitBypass
                && !calculation.explicitAllowMatched()) {
            throw new IllegalStateException(
                    "关系图内部入口缺少显式允许范围，已拒绝执行");
        }
        Map<String, Object> parameters = permission.getSqlParameters() == null
                ? Map.of() : Map.copyOf(permission.getSqlParameters());
        DataScopePlan scope = new DataScopePlan(
                permission.isHasPermission(),
                sql,
                parameters,
                List.of(),
                permission.getMatchedRuleNames() == null
                        ? List.of()
                        : List.copyOf(permission.getMatchedRuleNames()),
                permission.getExplanation(),
                permission.getReleaseVersion());
        return new Grant(
                hopIndex,
                entityCode,
                historyId,
                schemaHash,
                AccessMode.INTERNAL_SAFE,
                INTERNAL_SCOPE_KEY,
                null,
                scope);
    }

    private SysUser currentUser() {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new ForbiddenException("关系图读取缺少已认证用户上下文");
        }
        SysUser user = userService.getById(userId);
        if (user == null
                || !SysUser.Status.ENABLED.getValue().equals(user.getStatus())) {
            throw new ForbiddenException("当前用户不存在或已停用");
        }
        return user;
    }
}
