package com.workflow.service;

import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityListConfig;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityListConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UiConfigurationAccessService {

    private final CurrentUserRoleService currentUserRoleService;
    private final EntityDefinitionAccessPolicy entityAccessPolicy;
    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listConfigMapper;

    public void requireGlobalConfigurationAccess() {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护全局 UI 扩展、数据源和组件模板");
    }

    public void requireFormAccess(String formId) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护实体表单配置");
        EntityForm form = formMapper.selectById(formId);
        if (form == null) {
            throw new IllegalArgumentException("表单不存在: " + formId);
        }
        entityAccessPolicy.requireDynamicById(form.getEntityId());
    }

    public void requireEntityFormAccess(String entityId) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以查看或维护实体表单配置");
        if (!StringUtils.hasText(entityId)) {
            throw new IllegalArgumentException("实体ID不能为空");
        }
        entityAccessPolicy.requireDynamicById(entityId);
    }

    public void requireNewFormAccess(EntityForm form) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护实体表单配置");
        if (form == null || !StringUtils.hasText(form.getEntityId())) {
            throw new IllegalArgumentException("表单实体ID不能为空");
        }
        entityAccessPolicy.requireDynamicById(form.getEntityId());
    }

    public void requireListAccess(String listId) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护实体列表配置");
        EntityListConfig config = listConfigMapper.selectById(listId);
        if (config == null) {
            throw new IllegalArgumentException("列表配置不存在: " + listId);
        }
        entityAccessPolicy.requireDynamicById(config.getEntityId());
    }

    public void requireNewListAccess(EntityListConfig config) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护实体列表配置");
        if (config == null) {
            throw new IllegalArgumentException("列表配置不能为空");
        }
        if (StringUtils.hasText(config.getEntityId())) {
            entityAccessPolicy.requireDynamicById(config.getEntityId());
            return;
        }
        entityAccessPolicy.requireDynamicByCode(config.getEntityCode());
    }

    public void requireNewListAccess(EntityListConfigDTO config) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护实体列表配置");
        if (config == null) {
            throw new IllegalArgumentException("列表配置不能为空");
        }
        if (StringUtils.hasText(config.getEntityId())) {
            entityAccessPolicy.requireDynamicById(config.getEntityId());
            return;
        }
        entityAccessPolicy.requireDynamicByCode(config.getEntityCode());
    }
}
