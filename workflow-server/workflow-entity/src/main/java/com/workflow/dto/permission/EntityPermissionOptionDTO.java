package com.workflow.dto.permission;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 实体权限选择项。
 */
@Data
@AllArgsConstructor
public class EntityPermissionOptionDTO {
    /** 操作动作（如 view/create/update/delete） */
    private String action;
    /** 权限码 */
    private String code;
    /** 显示名称 */
    private String label;
    /** 描述说明 */
    private String description;
    /** 分类（用于前端分组展示） */
    private String category;
}
