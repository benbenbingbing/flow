package com.workflow.service.permission;

import java.util.Arrays;
import java.util.Locale;

/**
 * 实体列表标准功能权限。
 */
public enum EntityPermissionAction {
    LIST("list", "查询列表", "访问、查询和统计实体列表"),
    VIEW("view", "查看", "查看实体数据详情"),
    CREATE("create", "新增", "新增实体数据；流程实体就绪时可选择发起流程"),
    UPDATE("update", "编辑", "修改实体数据"),
    DELETE("delete", "单条删除", "删除单条实体数据"),
    BATCH_DELETE("batch-delete", "批量删除", "批量删除实体数据"),
    EXPORT("export", "导出选中", "导出选中的实体数据"),
    EXPORT_ALL("export-all", "导出全部", "导出当前数据权限范围内的全部数据"),
    APPROVE("approve", "审批", "查看并处理当前审批任务");

    private final String code;
    private final String label;
    private final String description;

    EntityPermissionAction(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 拼接实体标准功能权限码，格式为 entity:{实体编码}:{动作码}。
     *
     * @param entityCode 实体编码
     * @return 标准权限码字符串
     */
    public String permissionCode(String entityCode) {
        return "entity:" + normalizeEntityCode(entityCode) + ":" + code;
    }

    /**
     * 根据前端按钮 key 解析对应的标准动作。
     *
     * @param buttonKey 前端按钮 key，如 create、edit、batchDelete
     * @return 对应动作枚举，无法识别返回 null
     */
    public static EntityPermissionAction fromButtonKey(String buttonKey) {
        if (buttonKey == null) {
            return null;
        }
        return switch (buttonKey) {
            case "create" -> CREATE;
            case "view" -> VIEW;
            case "edit" -> UPDATE;
            case "delete" -> DELETE;
            case "batchDelete" -> BATCH_DELETE;
            case "exportSelected" -> EXPORT;
            case "exportAll" -> EXPORT_ALL;
            case "approve" -> APPROVE;
            default -> null;
        };
    }

    /**
     * 根据动作码解析枚举。
     *
     * @param code 动作码，大小写不敏感
     * @return 对应动作枚举，无法识别返回 null
     */
    public static EntityPermissionAction fromCode(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(action -> action.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 规范化实体编码：转小写并校验只允许小写字母、数字和下划线，须以字母开头。
     *
     * @param entityCode 原始实体编码
     * @return 规范化后的实体编码
     * @throws IllegalArgumentException 实体编码为空或格式不正确时抛出
     */
    public static String normalizeEntityCode(String entityCode) {
        if (entityCode == null || entityCode.isBlank()) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        String normalized = entityCode.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("实体编码格式不正确: " + entityCode);
        }
        return normalized;
    }
}
