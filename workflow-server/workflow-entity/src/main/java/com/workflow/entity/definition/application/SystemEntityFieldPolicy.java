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

    /**
     * 判断是否界面{@code configurable}；判断结果决定调用方的后续分支。
     *
     * @param entity 实体，作为 {@code isRuntimeReadable} 的输入影响后续处理
     * @param field 字段，作为 {@code isRuntimeReadable} 的输入影响后续处理
     * @return 界面{@code configurable}条件成立时为 true，否则为 false
     */
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

    /**
     * 判断是否运行时可读；判断结果决定调用方的后续分支。
     *
     * @param entity 实体，供本方法判断是否运行时可读时使用
     * @param field 字段，作为 {@code isSensitive} 的输入影响后续处理
     * @return 运行时可读条件成立时为 true，否则为 false
     */
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

    /**
     * 判断是否{@code sensitive}；判断结果决定调用方的后续分支。
     *
     * @param fieldCode 字段编码，后续用于判断是否{@code sensitive}时定位或关联目标
     * @return {@code sensitive}条件成立时为 true，否则为 false
     */
    public boolean isSensitive(String fieldCode) {
        return StringUtils.hasText(fieldCode)
                && SENSITIVE_FIELD.matcher(fieldCode).find();
    }

    /**
     * 整理必填{@code permissions}数据，供调用方遍历或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 系统实体字段策略集合，供调用方遍历或展示
     */
    public List<String> requiredPermissions(String entityCode) {
        return REQUIRED_PERMISSIONS.getOrDefault(
                normalize(entityCode),
                List.of());
    }

    /**
     * 判断是否{@code supported}实体；判断结果决定调用方的后续分支。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return {@code supported}实体条件成立时为 true，否则为 false
     */
    public boolean isSupportedEntity(String entityCode) {
        return REQUIRED_PERMISSIONS.containsKey(
                normalize(entityCode));
    }

    /**
     * 生成展示字段文本，供后续匹配或展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 处理后的展示字段文本，供调用方比较或展示
     */
    public String displayField(String entityCode) {
        return DISPLAY_FIELDS.get(normalize(entityCode));
    }

    /**
     * 处理引用类型，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fieldCode 字段编码，后续用于处理引用类型时定位或关联目标
     * @return 处理后的引用类型结果，供调用方继续处理
     */
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

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化系统实体字段策略的原始输入，结果供调用方继续使用
     * @return 规范化后的系统实体字段策略文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
