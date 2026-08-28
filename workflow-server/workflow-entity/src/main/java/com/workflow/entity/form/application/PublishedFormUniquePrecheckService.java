package com.workflow.entity.form.application;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.form.api.request.FormUniquePrecheckRequest;
import com.workflow.entity.form.api.response.FormUniquePrecheckResponse;
import com.workflow.entity.form.application.model.FormUniqueCheck;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.permission.api.response.DataPermissionResult;
import com.workflow.entity.permission.application.DataPermissionEngine;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 表单唯一性提前检查应用服务。
 *
 * <p>服务端根据签名令牌或当前 ACTIVE 发布版重新解析规则，客户端只能指定规则标识，
 * 不能提交条件、规范化方式或错误文案。此接口只改善填写反馈，提交事务仍需重新校验。</p>
 */
@Service
@RequiredArgsConstructor
public class PublishedFormUniquePrecheckService {

    private final UiConfigReleaseService releaseService;
    private final PublishedFormUniqueRuleService uniqueRuleService;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityActionCapabilityService capabilityService;
    private final SysUserService userService;
    private final DataPermissionEngine dataPermissionEngine;

    /** 按当前用户可使用的精确表单发布版执行提前查重。 */
    @Transactional(readOnly = true)
    public FormUniquePrecheckResponse precheck(
            String formId,
            FormUniquePrecheckRequest request) {
        if (!StringUtils.hasText(formId)) {
            throw new IllegalArgumentException("表单ID不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("唯一性预检请求不能为空");
        }
        if (!StringUtils.hasText(request.getRuleId())
                && !StringUtils.hasText(request.getFieldCode())) {
            throw new IllegalArgumentException(
                    "唯一性预检必须提供 ruleId 或 fieldCode");
        }
        ResolvedEntityFormRelease resolved = releaseService
                .resolveAuthorizedRuntimeFormRelease(
                        formId,
                        request.getReleaseId(),
                        request.getReleaseVersion(),
                        request.getReleaseResolutionToken());
        EntityDefinition definition = requireDefinition(resolved);
        requireMutationPermission(
                definition.getEntityCode(),
                request.getRecordId());

        // 唯一性提交终检必须覆盖实体全部数据，但提前提示是可选 UX。受数据范围
        // 限制时若继续做全局查询，客户端可利用 recordId 的合并/排除差分关联
        // 范围外记录；若只过滤候选，又会错误提示“可用”。因此仅对确实拥有
        // 全量读取范围的用户开放全局预检，其他用户统一跳过并依赖提交终检。
        if (!canRevealGlobalUniqueness(
                definition.getEntityCode())) {
            return FormUniquePrecheckResponse.from(
                    FormUniqueCheck.skipped(
                            request.getRuleId(),
                            request.getFieldCode()));
        }

        // 没有 ACTIVE 发布版时解析服务会返回草稿对象；唯一规则必须 fail-open 跳过草稿。
        if (!StringUtils.hasText(resolved.releaseId())) {
            return FormUniquePrecheckResponse.from(
                    FormUniqueCheck.skipped(
                            request.getRuleId(),
                            request.getFieldCode()));
        }
        return FormUniquePrecheckResponse.from(
                uniqueRuleService.check(
                        resolved.form(),
                        definition.getEntityCode(),
                        request.getRuleId(),
                        request.getFieldCode(),
                        request.getRecordId(),
                        request.getFormData() == null
                                ? Map.of() : request.getFormData(),
                        true));
    }

    private EntityDefinition requireDefinition(
            ResolvedEntityFormRelease resolved) {
        if (resolved == null || resolved.form() == null
                || !StringUtils.hasText(
                        resolved.form().getEntityId())) {
            throw new IllegalArgumentException("已发布表单未绑定实体");
        }
        EntityDefinition definition = definitionMapper.selectById(
                resolved.form().getEntityId());
        if (definition == null || !StringUtils.hasText(
                definition.getEntityCode())) {
            throw new IllegalArgumentException("表单绑定的实体不存在");
        }
        return definition;
    }

    private void requireMutationPermission(
            String entityCode,
            String recordId) {
        if (!StringUtils.hasText(recordId)) {
            capabilityService.requireStandardPermission(
                    entityCode,
                    EntityPermissionAction.CREATE);
            return;
        }
        // 审批表单也可能在提交前做唯一性预检，因此编辑记录接受 UPDATE 或 APPROVE。
        capabilityService.requireAnyStandardPermission(
                entityCode,
                EntityPermissionAction.UPDATE,
                EntityPermissionAction.APPROVE);
    }

    /**
     * 判断当前用户能否看到全局唯一占用状态。
     *
     * <p>这里故意使用实体默认数据范围且只接受 allow-all；任何过滤、拒绝、
     * 缺失用户或损坏权限结果都不能进入无 DataScope 的唯一候选查询。</p>
     */
    private boolean canRevealGlobalUniqueness(
            String entityCode) {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        SysUser user = userService.getById(userId);
        if (user == null) {
            return false;
        }
        DataPermissionResult permission = dataPermissionEngine
                .calculatePermission(entityCode, null, user);
        return permission != null
                && permission.isHasPermission()
                && !permission.isNeedFilter();
    }
}
