package com.workflow.entity.ui.application;

import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;

import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * UI 配置访问控制服务，统一校验当前用户对表单、列表及全局 UI 扩展的维护权限。
 *
 * <p>所有维护操作均要求管理员角色。动态实体和平台系统实体都允许维护
 * 表单、列表展示配置；系统实体结构仍由结构访问策略保护。</p>
 */
@Service
@RequiredArgsConstructor
public class UiConfigurationAccessService {

    private final CurrentUserRoleService currentUserRoleService;
    private final EntityUiConfigurationPolicy entityUiConfigurationPolicy;
    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listConfigMapper;

    /**
     * 要求当前用户为管理员，用于全局 UI 扩展、数据源和组件模板维护。
     */
    public void requireGlobalConfigurationAccess() {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护全局 UI 扩展、数据源和组件模板");
    }

    /**
     * 校验当前用户可执行 UI 配置热修复。
     *
     * @param ignoredOverrideRisk 兼容旧调用签名，REVIEW 不再需要额外覆盖权限
     */
    public void requireHotfixAccess(boolean ignoredOverrideRisk) {
        if (currentUserRoleService.isSuperAdmin()) {
            return;
        }
        if (!PermissionUtil.hasPermission("entity:ui-config:hotfix")) {
            throw new BusinessForbiddenException(
                    "UI_HOTFIX_PERMISSION_REQUIRED",
                    "没有 UI 配置兼容热修复权限");
        }
    }

    /**
     * 校验当前用户可维护指定表单配置。
     *
     * @param formId 表单ID
     * @throws IllegalArgumentException 表单不存在时抛出
     * @throws BusinessConflictException 表单归属实体为系统实体时抛出
     */
    public void requireFormAccess(String formId) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护实体表单配置");
        EntityForm form = formMapper.selectById(formId);
        if (form == null) {
            throw new IllegalArgumentException("表单不存在: " + formId);
        }
        entityUiConfigurationPolicy.requireConfigurableById(
                form.getEntityId());
    }

    /**
     * 校验当前用户可查看或维护指定实体的表单配置。
     *
     * @param entityId 实体ID
     * @throws IllegalArgumentException 实体ID为空时抛出
     * @throws BusinessConflictException 实体为系统实体时抛出
     */
    public void requireEntityFormAccess(String entityId) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以查看或维护实体表单配置");
        if (!StringUtils.hasText(entityId)) {
            throw new IllegalArgumentException("实体ID不能为空");
        }
        entityUiConfigurationPolicy.requireConfigurableById(entityId);
    }

    /**
     * 校验当前用户可创建新的表单配置（尚未落库的表单）。
     *
     * @param form 待创建的表单，须携带实体ID
     * @throws IllegalArgumentException 表单或实体ID为空时抛出
     * @throws BusinessConflictException 实体为系统实体时抛出
     */
    public void requireNewFormAccess(EntityForm form) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护实体表单配置");
        if (form == null || !StringUtils.hasText(form.getEntityId())) {
            throw new IllegalArgumentException("表单实体ID不能为空");
        }
        entityUiConfigurationPolicy.requireConfigurableById(
                form.getEntityId());
    }

    /**
     * 校验当前用户可维护指定列表配置。
     *
     * @param listId 列表配置ID
     * @throws IllegalArgumentException 列表不存在时抛出
     * @throws BusinessConflictException 列表归属实体为系统实体时抛出
     */
    public void requireListAccess(String listId) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护实体列表配置");
        EntityListConfig config = listConfigMapper.selectById(listId);
        if (config == null) {
            throw new IllegalArgumentException("列表配置不存在: " + listId);
        }
        entityUiConfigurationPolicy.requireConfigurableById(
                config.getEntityId());
    }

    /**
     * 校验当前用户可创建新的列表配置（尚未落库的列表实体对象）。
     *
     * @param config 待创建的列表配置，按实体ID或实体编码校验
     * @throws IllegalArgumentException 配置为空时抛出
     * @throws BusinessConflictException 实体为系统实体时抛出
     */
    public void requireNewListAccess(EntityListConfig config) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护实体列表配置");
        if (config == null) {
            throw new IllegalArgumentException("列表配置不能为空");
        }
        if (StringUtils.hasText(config.getEntityId())) {
            entityUiConfigurationPolicy.requireConfigurableById(
                    config.getEntityId());
            return;
        }
        entityUiConfigurationPolicy.requireConfigurableByCode(
                config.getEntityCode());
    }

    /**
     * 校验当前用户可创建新的列表配置（尚未落库的列表 DTO）。
     *
     * @param config 待创建的列表配置 DTO，按实体ID或实体编码校验
     * @throws IllegalArgumentException 配置为空时抛出
     * @throws BusinessConflictException 实体为系统实体时抛出
     */
    public void requireNewListAccess(EntityListConfigDTO config) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护实体列表配置");
        if (config == null) {
            throw new IllegalArgumentException("列表配置不能为空");
        }
        if (StringUtils.hasText(config.getEntityId())) {
            entityUiConfigurationPolicy.requireConfigurableById(
                    config.getEntityId());
            return;
        }
        entityUiConfigurationPolicy.requireConfigurableByCode(
                config.getEntityCode());
    }
}
