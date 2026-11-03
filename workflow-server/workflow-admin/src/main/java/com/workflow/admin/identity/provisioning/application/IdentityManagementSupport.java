package com.workflow.admin.identity.provisioning.application;

import com.workflow.admin.identity.extension.application.IdentityExtensionService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** 连接现有管理入口和新来源记录：保留来源主权，并让本地扩展参与原保存事务。 */
@Component
@RequiredArgsConstructor
public class IdentityManagementSupport {
    private final IdentitySourceStore store;
    private final IdentityExtensionService extensions;

    public void lock() { store.lockDirectory(); }
    /** 拒绝本地覆盖来源字段，并补齐局部请求，避免后续默认值逻辑改写来源快照。 */
    public void protectUser(SysUser requested, SysUser existing) {
        if (existing == null || store.findLocal(IdentityObjectType.USER, existing.getId()) == null) return;
        same(requested.getUsername(), existing.getUsername()); same(requested.getNickname(), existing.getNickname());
        same(requested.getEmail(), existing.getEmail()); same(requested.getPhone(), existing.getPhone());
        same(requested.getAvatar(), existing.getAvatar()); same(requested.getOrgId(), existing.getOrgId());
        same(requested.getDeptId(), existing.getDeptId());
        requested.setUsername(existing.getUsername()); requested.setNickname(existing.getNickname());
        requested.setEmail(existing.getEmail()); requested.setPhone(existing.getPhone());
        requested.setAvatar(existing.getAvatar()); requested.setOrgId(existing.getOrgId());
        requested.setDeptId(existing.getDeptId());
    }
    /** 来源组织字段保持数据库值；负责人投影继续由原职务任命入口维护。 */
    public void protectOrganization(SysOrganization requested, SysOrganization existing) {
        if (existing == null || store.findLocal(IdentityObjectType.ORGANIZATION, existing.getId()) == null) return;
        same(requested.getOrgCode(), existing.getOrgCode()); same(requested.getOrgName(), existing.getOrgName());
        same(requested.getType(), existing.getType()); same(requested.getParentId(), existing.getParentId());
        same(requested.getBusinessLevelCode(), existing.getBusinessLevelCode());
        same(requested.getPhone(), existing.getPhone()); same(requested.getEmail(), existing.getEmail());
        same(requested.getAddress(), existing.getAddress()); same(requested.getDescription(), existing.getDescription());
        if (requested.getSortOrder() != null && !Objects.equals(requested.getSortOrder(), existing.getSortOrder())) denied();
        requested.setOrgCode(existing.getOrgCode()); requested.setOrgName(existing.getOrgName());
        requested.setType(existing.getType()); requested.setParentId(existing.getParentId());
        requested.setBusinessLevelCode(existing.getBusinessLevelCode()); requested.setSortOrder(existing.getSortOrder());
        requested.setPhone(existing.getPhone()); requested.setEmail(existing.getEmail());
        requested.setAddress(existing.getAddress()); requested.setDescription(existing.getDescription());
    }
    public String status(IdentityObjectType target, String id, String requested) { return store.localStatus(target, id, requested); }
    public void retire(IdentityObjectType target, String id) { store.retire(target, id); }
    public void saveExtensions(IdentityObjectType target, String id, Map<String, IdentityExtensionEdit> edits) {
        extensions.saveLocal(target, id, edits);
    }
    public void fill(SysUser user) {
        user.setSourceInfo(store.info(IdentityObjectType.USER, user.getId()));
        user.setExtensionValues(extensions.readAll(IdentityObjectType.USER, user.getId()));
    }
    public void fill(SysOrganization org) {
        org.setSourceInfo(store.info(IdentityObjectType.ORGANIZATION, org.getId()));
        org.setExtensionValues(extensions.readAll(IdentityObjectType.ORGANIZATION, org.getId()));
    }
    private static void same(String requested, String existing) {
        // null 沿用管理接口“未提供”的历史约定；非 null 空串仍会参与主权比较。
        if (requested != null && !Objects.equals(blank(requested), blank(existing))) denied();
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }
    private static void denied() { throw new IdentityWriteException("SOURCE_FIELD_READ_ONLY", "base", "来源基础资料请在所属业务系统维护"); }
}
