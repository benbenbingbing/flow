package com.workflow.service.permission;

import com.workflow.common.ForbiddenException;
import com.workflow.common.PermissionUtil;
import com.workflow.common.UserContext;
import com.workflow.dto.EntityDataDTO;
import com.workflow.dto.permission.EntityActionCapabilityDTO;
import com.workflow.dto.permission.EntityActionRuleDTO;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.SysUser;
import com.workflow.mapper.EntityStatusMapper;
import com.workflow.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public void requireStandardPermission(String entityCode, EntityPermissionAction action) {
        String permissionCode = action.permissionCode(entityCode);
        if (!PermissionUtil.hasPermission(permissionCode)) {
            deny(entityCode, action.getCode(), null, "缺少权限：" + permissionCode);
        }
    }

    public void requireAnyStandardPermission(String entityCode, EntityPermissionAction... actions) {
        for (EntityPermissionAction action : actions) {
            if (PermissionUtil.hasPermission(action.permissionCode(entityCode))) {
                return;
            }
        }
        deny(entityCode, "any", null, "缺少所需实体操作权限");
    }

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
            capabilities.put(key, evaluateButton(entityCode, button, null, user, null));
        }
        return capabilities;
    }

    public EntityActionCapabilityDTO evaluateRowAction(
            String entityCode,
            String listKey,
            String buttonKey,
            EntityDataDTO row) {
        Map<String, Object> button = actionConfigService.resolveButton(entityCode, listKey, buttonKey);
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

    public void requireToolbarAction(String entityCode, String listKey, String buttonKey) {
        Map<String, Object> button = actionConfigService.resolveButton(entityCode, listKey, buttonKey);
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

    public void requireRowAction(
            String entityCode,
            String listKey,
            String buttonKey,
            EntityDataDTO row) {
        EntityActionCapabilityDTO capability = evaluateRowAction(entityCode, listKey, buttonKey, row);
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
        if (!PermissionUtil.hasPermission(permissionCode)) {
            deny(entityCode, actionKey, row == null ? null : row.getId(), "缺少权限：" + permissionCode);
        }
        SysUser user = currentUser();
        EntityStatus status = row != null && StringUtils.hasText(row.getStatus())
                ? statusMapper.findByEntityAndCode(entityCode, row.getStatus())
                : null;
        if (!ruleEvaluator.evaluate(rule, row, user, status == null ? null : status.getStatusCategory())) {
            String reason = rule != null && StringUtils.hasText(rule.getMessage())
                    ? rule.getMessage()
                    : "当前数据不满足操作条件";
            deny(entityCode, actionKey, row == null ? null : row.getId(), reason);
        }
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
        if (ruleEvaluator.evaluate(rule, row, user, statusCategory)) {
            return EntityActionCapabilityDTO.allowed();
        }
        String reason = rule != null && StringUtils.hasText(rule.getMessage())
                ? rule.getMessage()
                : "当前数据不满足操作条件";
        return "DISABLE".equalsIgnoreCase(actionConfigService.unavailableBehavior(button))
                ? EntityActionCapabilityDTO.disabled(reason)
                : EntityActionCapabilityDTO.hidden(reason);
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
                UserContext.getUserId(),
                UserContext.getUsername(),
                entityCode,
                action,
                dataId,
                reason);
        throw new ForbiddenException(StringUtils.hasText(reason) ? reason : "没有权限执行该操作");
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
