package com.workflow.entity.definition.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 平台系统表字段的展示、安全和基础权限策略。
 */
@Service
public class SystemEntityFieldPolicy {

    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)(^password$|password_hash|token_version|(^|_)(secret|token|private_key|credential|salt|otp|mfa_secret)(_|$))");

    private static final Map<String, List<String>> REQUIRED_PERMISSIONS = Map.ofEntries(
            Map.entry("sys_user", List.of("system:user:view")),
            Map.entry("sys_group", List.of("system:user:view")),
            Map.entry("sys_user_group", List.of("system:user:view")),
            Map.entry("sys_role", List.of("system:role:view")),
            Map.entry("sys_organization", List.of("system:organization:view")),
            Map.entry("sys_menu", List.of("system:menu:view")),
            Map.entry("sys_dict", List.of("system:dictionary:view")),
            Map.entry("sys_dict_item", List.of("system:dictionary:view")),
            Map.entry("sys_user_role", List.of(
                    "system:user:view",
                    "system:role:view")),
            Map.entry("sys_role_menu", List.of(
                    "system:role:view",
                    "system:menu:view"))
    );

    private static final Map<String, String> DISPLAY_FIELDS = Map.ofEntries(
            Map.entry("sys_user", "nickname"),
            Map.entry("sys_role", "role_name"),
            Map.entry("sys_organization", "org_name"),
            Map.entry("sys_group", "group_name"),
            Map.entry("sys_menu", "menu_name"),
            Map.entry("sys_dict", "dict_name"),
            Map.entry("sys_dict_item", "item_label")
    );

    private static final Set<String> USER_FIELDS =
            Set.of("user_id", "leader_id", "create_by", "update_by");
    private static final Set<String> ROLE_FIELDS = Set.of("role_id");
    private static final Set<String> GROUP_FIELDS = Set.of("group_id");
    private static final Set<String> DEPT_FIELDS = Set.of(
            "org_id", "dept_id");
    private static final Set<String> MENU_FIELDS = Set.of("menu_id");
    private static final Set<String> DICT_FIELDS = Set.of("dict_id");

    public boolean isUiConfigurable(
            EntityDefinition entity,
            EntityField field) {
        if (entity == null || field == null) {
            return false;
        }
        if (entity.getStorageMode() != EntityDefinition.StorageMode.SYSTEM) {
            return true;
        }
        return isRuntimeReadable(entity, field);
    }

    public boolean isRuntimeReadable(
            EntityDefinition entity,
            EntityField field) {
        if (entity == null || field == null
                || !StringUtils.hasText(field.getFieldCode())) {
            return false;
        }
        if (entity.getStorageMode() != EntityDefinition.StorageMode.SYSTEM) {
            return true;
        }
        return !isSensitive(field.getFieldCode());
    }

    public boolean isSensitive(String fieldCode) {
        return StringUtils.hasText(fieldCode)
                && SENSITIVE_FIELD.matcher(fieldCode).find();
    }

    public List<String> requiredPermissions(String entityCode) {
        return REQUIRED_PERMISSIONS.getOrDefault(
                normalize(entityCode),
                List.of());
    }

    public boolean isSupportedEntity(String entityCode) {
        return REQUIRED_PERMISSIONS.containsKey(
                normalize(entityCode));
    }

    public String displayField(String entityCode) {
        return DISPLAY_FIELDS.get(normalize(entityCode));
    }

    public EntityField.RefEntityType referenceType(
            String entityCode,
            String fieldCode) {
        String normalizedField = normalize(fieldCode);
        if (USER_FIELDS.contains(normalizedField)) {
            return EntityField.RefEntityType.USER;
        }
        if (ROLE_FIELDS.contains(normalizedField)) {
            return EntityField.RefEntityType.ROLE;
        }
        if (GROUP_FIELDS.contains(normalizedField)) {
            return EntityField.RefEntityType.GROUP;
        }
        if (DEPT_FIELDS.contains(normalizedField)) {
            return EntityField.RefEntityType.DEPT;
        }
        if (MENU_FIELDS.contains(normalizedField)) {
            return EntityField.RefEntityType.MENU;
        }
        if (DICT_FIELDS.contains(normalizedField)) {
            return EntityField.RefEntityType.DICT;
        }
        if ("parent_id".equals(normalizedField)) {
            return switch (normalize(entityCode)) {
                case "sys_organization" ->
                        EntityField.RefEntityType.DEPT;
                case "sys_menu" ->
                        EntityField.RefEntityType.MENU;
                case "sys_dict_item" ->
                        EntityField.RefEntityType.DICT_ITEM;
                default -> null;
            };
        }
        return null;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
