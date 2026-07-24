package com.workflow.service.permission;

import com.workflow.dto.permission.EntityPermissionOptionDTO;

import java.util.List;

/**
 * 自定义实体权限选项扩展点。
 *
 * <p>业务模块可实现该接口，将自定义权限加入列表按钮权限选择器。</p>
 */
public interface EntityPermissionOptionProvider {

    /**
     * 返回自定义权限选项列表，用于注入到权限选择器。
     *
     * @param entityCode 实体编码
     * @return 自定义权限选项列表
     */
    List<EntityPermissionOptionDTO> getOptions(String entityCode);

    /**
     * 判断当前提供器是否支持指定权限码，用于校验自定义权限归属。
     *
     * @param entityCode     实体编码
     * @param permissionCode 权限码
     * @return 支持返回 true，默认 false
     */
    default boolean supportsPermission(String entityCode, String permissionCode) {
        return false;
    }

    /**
     * 校验自定义权限码的合法性。
     *
     * @param entityCode     实体编码
     * @param permissionCode 权限码
     */
    default void validatePermission(String entityCode, String permissionCode) {
    }
}
