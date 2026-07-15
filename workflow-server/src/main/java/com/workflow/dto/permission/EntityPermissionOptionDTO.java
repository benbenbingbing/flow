package com.workflow.dto.permission;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 实体权限选择项。
 */
@Data
@AllArgsConstructor
public class EntityPermissionOptionDTO {
    private String action;
    private String code;
    private String label;
    private String description;
    private String category;
}
