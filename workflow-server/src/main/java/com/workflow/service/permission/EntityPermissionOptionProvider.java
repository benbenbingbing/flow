package com.workflow.service.permission;

import com.workflow.dto.permission.EntityPermissionOptionDTO;

import java.util.List;

/**
 * 自定义实体权限选项扩展点。
 *
 * <p>业务模块可实现该接口，将自定义权限加入列表按钮权限选择器。</p>
 */
public interface EntityPermissionOptionProvider {

    List<EntityPermissionOptionDTO> getOptions(String entityCode);

    default boolean supportsPermission(String entityCode, String permissionCode) {
        return false;
    }

    default void validatePermission(String entityCode, String permissionCode) {
    }
}
