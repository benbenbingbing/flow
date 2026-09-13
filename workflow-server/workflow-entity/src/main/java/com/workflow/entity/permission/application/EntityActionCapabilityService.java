package com.workflow.entity.permission.application;

import com.workflow.core.logging.LogValue;
import com.workflow.core.error.ForbiddenException;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.contracts.process.port.ProcessTaskAccessPort.ActionableTaskContext;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 计算和强制校验实体按钮能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityActionCapabilityService {

    private final EntityListActionConfigService actionConfigService;
    private final EntityActionRuleEvaluator ruleEvaluator;
    private final EntityStatusMapper statusMapper;
    private final SysUserService userService;
    private final CurrentProcessTaskAssigneeLookup assigneeLookup;

    /**
     * 强制要求当前用户拥有指定标准动作权限，否则抛出禁止访问异常。
     *
     * @param entityCode 实体编码
     * @param action     标准动作
     * @throws ForbiddenException 缺少权限时抛出
     */
    public void requireStandardPermission(String entityCode, EntityPermissionAction action) {
        String permissionCode = action.permissionCode(entityCode);
        if (!PermissionUtil.hasPermission(permissionCode)) {
            deny(entityCode, action.getCode(), null, "缺少权限：" + permissionCode);
        }
    }

    /**
     * 读取实体元数据（按编码查询定义）所需的最低权限。
     * 设计人员可凭 entity:definition:view 访问；业务用户只要拥有该实体任一标准动作权限即可，
     * 例如列表菜单授权后的 entity:{code}:list。
     *
     * @param entityCode 实体编码
     */
    public void requireEntityMetadataAccess(String entityCode) {
        if (PermissionUtil.hasPermission("entity:definition:view")) {
            return;
        }
        requireAnyStandardPermission(entityCode, EntityPermissionAction.values());
    }

    /**
     * 强制要求当前用户拥有给定动作中任意一个的权限。
     *
     * @param entityCode 实体编码
     * @param actions    候选标准动作集合
     * @throws ForbiddenException 全部缺失时抛出
     */
    public void requireAnyStandardPermission(String entityCode, EntityPermissionAction... actions) {
        for (EntityPermissionAction action : actions) {
            if (PermissionUtil.hasPermission(action.permissionCode(entityCode))) {
                return;
            }
        }
        deny(entityCode, "any", null, "缺少所需实体操作权限");
    }

    /**
     * 为数据行批量填充按钮操作能力（可见/可用/禁用原因）。
     *
     * @param entityCode 实体编码
     * @param config     列表配置，用于解析行内和工具栏按钮
     * @param rows       待填充的数据行列表，为空直接返回
     */
    public void enrichRows(
            String entityCode,
            EntityListConfig config,
            List<EntityDataDTO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        SysUser user = currentUser();
        Map<String, String> statusCategories = statusMapper.findByEntityCode(entityCode).stream()
                .filter(status -> StringUtils.hasText(status.getStatusCode()))
                .collect(Collectors.toMap(
                        EntityStatus::getStatusCode,
                        EntityStatus::getStatusCategory,
                        (left, right) -> left));
        List<Map<String, Object>> buttons = new java.util.ArrayList<>(
                actionConfigService.resolveRowButtons(config, entityCode));
        actionConfigService.resolveToolbarButtons(config, entityCode).stream()
                .filter(button -> List.of("batchDelete", "exportSelected")
                        .contains(asString(button.get("key"))))
                .forEach(buttons::add);
        for (EntityDataDTO row : rows) {
            Map<String, EntityActionCapabilityDTO> capabilities = new LinkedHashMap<>();
            for (Map<String, Object> button : buttons) {
                if (Boolean.FALSE.equals(button.get("enabled"))) {
                    continue;
                }
                String key = asString(button.get("key"));
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                capabilities.put(key, evaluateButton(
                        entityCode,
                        button,
                        row,
                        user,
                        statusCategories.get(row.getStatus())));
            }
            row.setActionCapabilities(capabilities);
        }
    }

    /**
     * 评估工具栏按钮的操作能力，返回每个按钮的能力描述。
     *
     * @param entityCode 实体编码
     * @param config     列表配置
     * @return 按钮 key 到能力描述的映射
     */
    public Map<String, EntityActionCapabilityDTO> evaluateToolbarActions(
            String entityCode,
            EntityListConfig config) {
        Map<String, EntityActionCapabilityDTO> capabilities = new LinkedHashMap<>();
        SysUser user = currentUser();
        for (Map<String, Object> button : actionConfigService.resolveToolbarButtons(config, entityCode)) {
            if (Boolean.FALSE.equals(button.get("enabled"))) {
                continue;
            }
            String key = asString(button.get("key"));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            capabilities.put(key,
                    List.of("batchDelete", "exportSelected").contains(key)
                            ? evaluateSelectionToolbarButton(
                                    entityCode, button, user)
                            : evaluateButton(
                                    entityCode, button, null, user, null));
        }
        return capabilities;
    }

    /**
     * 评估单行指定按钮的操作能力。
     *
     * @param entityCode 实体编码
     * @param listKey    列表编码
     * @param buttonKey  按钮 key
     * @param row        当前数据行
     * @return 按钮能力描述，按钮未启用时返回 hidden
     */
    public EntityActionCapabilityDTO evaluateRowAction(
            String entityCode,
            String listKey,
            String buttonKey,
            EntityDataDTO row) {
        return evaluateRowActionForConfig(
                entityCode,
                actionConfigService.resolveListConfig(
                        entityCode,
                        listKey),
                buttonKey,
                row);
    }

    public EntityActionCapabilityDTO evaluateRowActionForConfig(
            String entityCode,
            EntityListConfig config,
            String buttonKey,
            EntityDataDTO row) {
        Map<String, Object> button = actionConfigService.resolveButton(
                config,
                entityCode,
                buttonKey);
        if (button == null || Boolean.FALSE.equals(button.get("enabled"))) {
            return EntityActionCapabilityDTO.hidden("操作未启用");
        }
        EntityStatus status = StringUtils.hasText(row.getStatus())
                ? statusMapper.findByEntityAndCode(entityCode, row.getStatus())
                : null;
        return evaluateButton(
                entityCode,
                button,
                row,
                currentUser(),
                status == null ? null : status.getStatusCategory());
    }

    /**
     * 强制要求当前用户可执行指定工具栏按钮，否则抛出禁止访问异常。
     *
     * @param entityCode 实体编码
     * @param listKey    列表编码
     * @param buttonKey  按钮 key
     * @throws ForbiddenException 按钮未启用或不可见时抛出
     */
    public void requireToolbarAction(String entityCode, String listKey, String buttonKey) {
        requireToolbarActionForConfig(
                entityCode,
                actionConfigService.resolveListConfig(
                        entityCode,
                        listKey),
                buttonKey);
    }

    public void requireToolbarActionForConfig(
            String entityCode,
            EntityListConfig config,
            String buttonKey) {
        Map<String, Object> button = actionConfigService.resolveButton(
                config,
                entityCode,
                buttonKey);
        if (button == null || Boolean.FALSE.equals(button.get("enabled"))) {
            deny(entityCode, buttonKey, null, "操作未启用");
        }
        EntityActionCapabilityDTO capability = evaluateButton(
                entityCode,
                button,
                null,
                currentUser(),
                null);
        if (!capability.isVisible() || !capability.isEnabled()) {
            deny(entityCode, buttonKey, null, capability.getReason());
        }
    }

    /**
     * 强制要求当前用户可执行指定行内按钮，否则抛出禁止访问异常。
     *
     * @param entityCode 实体编码
     * @param listKey    列表编码
     * @param buttonKey  按钮 key
     * @param row        当前数据行
     * @throws ForbiddenException 按钮不可见或不可用时抛出
     */
    public void requireRowAction(
            String entityCode,
            String listKey,
            String buttonKey,
            EntityDataDTO row) {
        requireRowActionForConfig(
                entityCode,
                actionConfigService.resolveListConfig(
                        entityCode,
                        listKey),
                buttonKey,
                row);
    }

    public void requireRowActionForConfig(
            String entityCode,
            EntityListConfig config,
            String buttonKey,
            EntityDataDTO row) {
        EntityActionCapabilityDTO capability = evaluateRowActionForConfig(
                entityCode,
                config,
                buttonKey,
                row);
        if (!capability.isVisible() || !capability.isEnabled()) {
            deny(entityCode, buttonKey, row == null ? null : row.getId(), capability.getReason());
        }
    }

    /**
     * 自定义后端动作统一鉴权入口。
     */
    public void requireCustomAction(
            String entityCode,
            String actionKey,
            String permissionCode,
            EntityActionRuleDTO rule,
            EntityDataDTO row) {
        EntityActionCapabilityDTO capability =
                evaluateConfiguredAction(
                        entityCode,
                        permissionCode,
                        rule,
                        row);
        if (!capability.isVisible() || !capability.isEnabled()) {
            deny(
                    entityCode,
                    actionKey,
                    row == null ? null : row.getId(),
                    capability.getReason());
        }
    }

    /**
     * 对已经从同一列表发布快照定位出的按钮执行最终能力校验。
     *
     * <p>自定义 key 不允许回退 UPDATE；它必须携带显式权限码。调用方负责从
     * 已验证快照按位置和 key 找到按钮，并为行/选择动作传入服务端重载的数据。</p>
     */
    public void requirePublishedListButton(
            String entityCode,
            String actionKey,
            Map<String, Object> button,
            EntityDataDTO row) {
        requirePublishedButtonIdentity(
                entityCode, actionKey, button,
                row == null ? null : row.getId());
        EntityStatus status = row != null
                && StringUtils.hasText(row.getStatus())
                ? statusMapper.findByEntityAndCode(
                        entityCode, row.getStatus())
                : null;
        EntityActionCapabilityDTO capability = evaluateButton(
                entityCode,
                button,
                row,
                currentUser(),
                status == null ? null : status.getStatusCategory());
        if (!capability.isVisible() || !capability.isEnabled()) {
            deny(entityCode, actionKey,
                    row == null ? null : row.getId(),
                    capability.getReason());
        }
    }

    /**
     * 对选择集工具栏按钮按“全部显示、再全部启用”的顺序执行最终校验。
     *
     * <p>数据必须由调用方按当前列表数据范围重新加载。先遍历所有行的
     * visibleWhen，再遍历 enabledWhen，避免先遇到禁用行时泄露后续本应
     * 隐藏的记录状态。</p>
     */
    public void requirePublishedListButton(
            String entityCode,
            String actionKey,
            Map<String, Object> button,
            List<EntityDataDTO> rows) {
        String recordId = rows == null || rows.isEmpty()
                ? null : rows.get(0).getId();
        requirePublishedButtonIdentity(
                entityCode, actionKey, button, recordId);
        EntityActionRuleDTO rule = actionConfigService.readRule(button);
        SysUser user = currentUser();
        List<EntityDataDTO> targets = rows == null || rows.isEmpty()
                ? java.util.Collections.singletonList(null)
                : rows;

        for (EntityDataDTO row : targets) {
            if (rule != null && !ruleEvaluator.evaluate(
                    rule.getVisibleWhen(), row, user,
                    statusCategory(entityCode, row))) {
                deny(entityCode, actionKey,
                        row == null ? null : row.getId(),
                        "当前数据不满足显示条件");
            }
        }
        for (EntityDataDTO row : targets) {
            if (rule != null && !ruleEvaluator.evaluate(
                    rule.getEnabledWhen(), row, user,
                    statusCategory(entityCode, row))) {
                deny(entityCode, actionKey,
                        row == null ? null : row.getId(),
                        StringUtils.hasText(rule.getDisabledMessage())
                                ? rule.getDisabledMessage()
                                : "当前数据不满足启用条件");
            }
        }
    }

    private void requirePublishedButtonIdentity(
            String entityCode,
            String actionKey,
            Map<String, Object> button,
            String recordId) {
        if (button == null
                || !Objects.equals(actionKey, asString(button.get("key")))
                || Boolean.FALSE.equals(button.get("enabled"))) {
            deny(entityCode, actionKey, recordId,
                    "按钮不存在或未启用");
        }
        String permissionCode = actionConfigService.permissionFor(
                entityCode, button);
        if (EntityPermissionAction.fromButtonKey(actionKey) == null
                && !StringUtils.hasText(permissionCode)) {
            deny(entityCode, actionKey, recordId,
                    "自定义按钮未配置权限");
        }
        if (!PermissionUtil.hasPermission(permissionCode)) {
            deny(entityCode, actionKey, recordId, "无操作权限");
        }
    }

    private String statusCategory(
            String entityCode,
            EntityDataDTO row) {
        if (row == null || !StringUtils.hasText(row.getStatus())) {
            return null;
        }
        EntityStatus status = statusMapper.findByEntityAndCode(
                entityCode, row.getStatus());
        return status == null ? null : status.getStatusCategory();
    }

    /**
     * 评估任意受控动作配置，供表单和列表共享权限与适用条件语义。
     *
     * @param entityCode    实体编码
     * @param permissionCode 动作权限码
     * @param rule          结构化适用条件
     * @param row           当前数据，可为空
     * @return 当前用户的动作能力
     */
    public EntityActionCapabilityDTO evaluateConfiguredAction(
            String entityCode,
            String permissionCode,
            EntityActionRuleDTO rule,
            EntityDataDTO row) {
        if (!PermissionUtil.hasPermission(permissionCode)) {
            return EntityActionCapabilityDTO.hidden(
                    "缺少权限：" + permissionCode);
        }
        SysUser user = currentUser();
        EntityStatus status =
                row != null && StringUtils.hasText(row.getStatus())
                        ? statusMapper.findByEntityAndCode(
                                entityCode,
                                row.getStatus())
                        : null;
        return evaluateConditions(
                new EntityActionRuleDTO[] {rule},
                row,
                user,
                status == null ? null : status.getStatusCategory(),
                false);
    }

    /**
     * 评估内置提交审批按钮，同时保留标准审批权限、真实任务绑定和发布规则约束。
     *
     * <p>候选人打开审批表单时尚未认领；只在此审批入口把办理人关系解释为已验证的
     * 可审批身份。默认规则与覆盖规则必须同时满足，覆盖不能取消流程状态等前置条件。
     * 普通保存、编辑和自定义动作继续使用 evaluateConfiguredAction。</p>
     *
     * @param entityCode 实体编码，用于校验 APPROVE 标准权限
     * @param row 已通过记录访问校验的当前数据
     * @param mandatoryRule 内置审批规则，不受表单覆盖配置替换
     * @param overrideRule 已发布按钮的额外适用条件，可以为空
     * @return 允许时携带当前用户的实际任务 ID；任一显示条件失败时隐藏，
     *         否则任一启用条件失败时禁用
     */
    public EntityActionCapabilityDTO evaluateApprovalAction(
            String entityCode,
            EntityDataDTO row,
            EntityActionRuleDTO mandatoryRule,
            EntityActionRuleDTO overrideRule) {
        String permissionCode = EntityPermissionAction.APPROVE.permissionCode(entityCode);
        if (!PermissionUtil.hasPermission(permissionCode)) {
            return EntityActionCapabilityDTO.hidden("缺少权限：" + permissionCode);
        }
        SysUser user = currentUser();
        String actionableTaskId = assigneeLookup.findActionableTaskId(row, user).orElse(null);
        if (!StringUtils.hasText(actionableTaskId)) {
            // 实体 currentTaskId/assignee 可能来自兄弟任务或过期投影，不能作为审批授权。
            return EntityActionCapabilityDTO.hidden("当前用户没有可办理的审批任务");
        }
        EntityStatus status = row != null && StringUtils.hasText(row.getStatus())
                ? statusMapper.findByEntityAndCode(entityCode, row.getStatus()) : null;
        EntityActionCapabilityDTO conditions = evaluateConditions(
                new EntityActionRuleDTO[] {mandatoryRule, overrideRule},
                row,
                user,
                status == null ? null : status.getStatusCategory(),
                true);
        if (!conditions.isVisible() || !conditions.isEnabled()) {
            // 任务 ID 只会在所有条件通过后附加，失败能力不泄露待办标识。
            return conditions;
        }
        return EntityActionCapabilityDTO.allowedForTask(actionableTaskId);
    }

    /**
     * 返回与当前认证用户、已鉴权记录及确切 taskId 全部匹配的活动待办上下文。
     * 调用方仍须先执行审批权限和规则判断；本方法只完成可信任务身份绑定。
     */
    public java.util.Optional<ActionableTaskContext>
            findActionableApprovalTaskContext(
                    EntityDataDTO row,
                    String taskId) {
        return assigneeLookup.findActionableTaskContext(
                row, currentUser(), taskId);
    }

    private EntityActionCapabilityDTO evaluateButton(
            String entityCode,
            Map<String, Object> button,
            EntityDataDTO row,
            SysUser user,
            String statusCategory) {
        String permissionCode = actionConfigService.permissionFor(entityCode, button);
        if (!PermissionUtil.hasPermission(permissionCode)) {
            return EntityActionCapabilityDTO.hidden("无操作权限");
        }
        EntityActionRuleDTO rule = actionConfigService.readRule(button);
        boolean approveAction = "approve".equals(asString(button.get("key")));
        String actionableTaskId = null;
        if (approveAction) {
            actionableTaskId = assigneeLookup.findActionableTaskId(row, user).orElse(null);
            if (!StringUtils.hasText(actionableTaskId)) {
                // 流程摘要可能指向兄弟任务或已过期；先绑定当前用户真实可审批的任务。
                return EntityActionCapabilityDTO.hidden(
                        "当前用户没有可办理的审批任务");
            }
        }
        EntityActionCapabilityDTO conditions = evaluateConditions(
                new EntityActionRuleDTO[] {rule},
                row,
                user,
                statusCategory,
                approveAction);
        if (!conditions.isVisible() || !conditions.isEnabled()) {
            return conditions;
        }
        return approveAction
                ? EntityActionCapabilityDTO.allowedForTask(actionableTaskId)
                : EntityActionCapabilityDTO.allowed();
    }

    /**
     * 计算依赖“当前选中行”的工具栏按钮初始能力。
     *
     * <p>列表级请求没有数据行，因此只校验权限和完全不依赖行的条件。
     * 依赖行的 visibleWhen/enabledWhen 由 {@link #enrichRows} 为每行计算，
     * 再由前端按当前选中集合聚合，避免用 {@code row=null} 将按钮永久隐藏。</p>
     */
    private EntityActionCapabilityDTO evaluateSelectionToolbarButton(
            String entityCode,
            Map<String, Object> button,
            SysUser user) {
        String permissionCode =
                actionConfigService.permissionFor(entityCode, button);
        if (!PermissionUtil.hasPermission(permissionCode)) {
            return EntityActionCapabilityDTO.hidden("无操作权限");
        }
        EntityActionRuleDTO rule = actionConfigService.readRule(button);
        if (rule == null) {
            return EntityActionCapabilityDTO.allowed();
        }
        if (ruleEvaluator.isRowIndependent(rule.getVisibleWhen())
                && !ruleEvaluator.evaluate(
                        rule.getVisibleWhen(), null, user, null)) {
            return EntityActionCapabilityDTO.hidden(
                    "当前用户不满足显示条件");
        }
        if (ruleEvaluator.isRowIndependent(rule.getEnabledWhen())
                && !ruleEvaluator.evaluate(
                        rule.getEnabledWhen(), null, user, null)) {
            return EntityActionCapabilityDTO.disabled(
                    rule.getDisabledMessage());
        }
        return EntityActionCapabilityDTO.allowed();
    }

    /**
     * 以固定两阶段顺序计算一组按钮规则。
     *
     * <p>先完成所有 {@code visibleWhen} 判定，再执行任一
     * {@code enabledWhen}。这使审批的强制规则和表单覆盖规则遵循
     * 同一优先级，不会因为某条禁用条件先失败而暴露本应隐藏的按钮。</p>
     */
    private EntityActionCapabilityDTO evaluateConditions(
            EntityActionRuleDTO[] rules,
            EntityDataDTO row,
            SysUser user,
            String statusCategory,
            boolean approval) {
        for (EntityActionRuleDTO rule : rules) {
            if (rule != null && !evaluateCondition(
                    rule.getVisibleWhen(), row, user, statusCategory, approval)) {
                return EntityActionCapabilityDTO.hidden(
                        "当前数据不满足显示条件");
            }
        }
        for (EntityActionRuleDTO rule : rules) {
            if (rule != null && !evaluateCondition(
                    rule.getEnabledWhen(), row, user, statusCategory, approval)) {
                String reason = StringUtils.hasText(rule.getDisabledMessage())
                        ? rule.getDisabledMessage()
                        : "当前数据不满足启用条件";
                return EntityActionCapabilityDTO.disabled(reason);
            }
        }
        return EntityActionCapabilityDTO.allowed();
    }

    /** 审批入口允许真实候选人命中办理人关系，其他条件仍逐项计算。 */
    private boolean evaluateCondition(
            EntityActionRuleDTO.RuleNode condition,
            EntityDataDTO row,
            SysUser user,
            String statusCategory,
            boolean approval) {
        return approval
                ? ruleEvaluator.evaluateForApproval(
                        condition, row, user, statusCategory, true)
                : ruleEvaluator.evaluate(
                        condition, row, user, statusCategory);
    }

    private SysUser currentUser() {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        return userService.getById(userId);
    }

    private void deny(String entityCode, String action, String dataId, String reason) {
        log.warn(
                "实体操作被拒绝: userId={}, username={}, entityCode={}, action={}, dataId={}, reason={}",
                LogValue.safe(UserContext.getUserId()),
                LogValue.safe(UserContext.getUsername()),
                LogValue.safe(entityCode),
                LogValue.safe(action),
                LogValue.safe(dataId),
                LogValue.safe(reason));
        throw new ForbiddenException(StringUtils.hasText(reason) ? reason : "没有权限执行该操作");
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
