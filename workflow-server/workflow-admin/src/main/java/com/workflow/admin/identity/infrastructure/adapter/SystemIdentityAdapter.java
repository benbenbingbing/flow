package com.workflow.admin.identity.infrastructure.adapter;

import com.workflow.admin.identity.extension.application.IdentityExtensionService;
import com.workflow.admin.identity.position.infrastructure.adapter.OrganizationPositionDirectoryAdapter;
import com.workflow.admin.identity.provisioning.application.IdentityWriteService;
import com.workflow.port.system.SysOrganizationPort;
import com.workflow.port.system.SysRolePort;
import com.workflow.port.system.SysUserGroupPort;
import com.workflow.port.system.SysUserPort;
import com.workflow.port.system.SysUserRolePort;
import com.workflow.port.system.identity.model.*;
import com.workflow.port.system.identity.model.IdentityDirectoryModels.*;
import com.workflow.port.system.identity.position.model.InitiatorOrganizationSnapshot;
import com.workflow.port.system.identity.position.model.OrganizationBusinessLevelView;
import com.workflow.port.system.identity.position.model.OrganizationUnitStateView;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 按系统数据对象提供入口，读、同步与关系维护各自进入原授权和事务服务。 */
@Component
@RequiredArgsConstructor
public class SystemIdentityAdapter implements SysUserPort, SysOrganizationPort, SysRolePort, SysUserRolePort, SysUserGroupPort {
    private final IdentityDirectoryQueryAdapter directory;
    private final IdentityWriteService writes;
    private final IdentityExtensionService extensions;
    private final OrganizationPositionDirectoryAdapter organizations;
    private final IdentityMembershipManagementAdapter memberships;
    private final IdentityDirectoryAdapter groups;

    @Override
    public UserPage pageUsers(UserQuery query) {
        return directory.pageUsers(query);
    }

    @Override
    public List<IdentityUser> findUsersByIds(List<String> userIds) {
        return directory.findUsersByIds(userIds);
    }

    @Override
    public List<IdentityUser> findUsersByUsernames(List<String> usernames) {
        return directory.findUsersByUsernames(usernames);
    }

    @Override
    public IdentitySaveResult saveUser(UserSaveModel model) {
        return writes.saveUser(model);
    }

    @Override
    public List<IdentitySaveResult> saveUsers(List<UserSaveModel> models) {
        return writes.saveUsers(models);
    }

    @Override
    public <T extends UserExtension> Optional<T> findUserExtension(String userId, Class<T> extensionType) {
        return extensions.findUserExtension(userId, extensionType);
    }

    @Override
    public Optional<Organization> findOrganization(String organizationId) {
        return directory.findOrganization(organizationId);
    }

    @Override
    public List<Organization> listOrganizationChildren(String parentId, String type) {
        return directory.listOrganizationChildren(parentId, type);
    }

    @Override
    public List<OrganizationNode> listOrganizationTree(String type) {
        return directory.listOrganizationTree(type);
    }

    @Override
    public List<Organization> listOrganizationAncestors(String organizationId) {
        return directory.listOrganizationAncestors(organizationId);
    }

    @Override
    public IdentitySaveResult saveOrganization(OrganizationSaveModel model) {
        return writes.saveOrganization(model);
    }

    @Override
    public List<IdentitySaveResult> saveOrganizations(List<OrganizationSaveModel> models) {
        return writes.saveOrganizations(models);
    }

    @Override
    public <T extends OrganizationExtension> Optional<T> findOrganizationExtension(String organizationId, Class<T> extensionType) {
        return extensions.findOrganizationExtension(organizationId, extensionType);
    }

    @Override
    public InitiatorOrganizationSnapshot captureInitiatorSnapshot(String idOrUsername) {
        return organizations.captureInitiatorSnapshot(idOrUsername);
    }

    @Override
    public OrganizationUnitStateView requireActiveOrganizationUnit(
            String organizationUnitId) {
        return organizations.requireActiveOrganizationUnit(organizationUnitId);
    }

    @Override
    public boolean isOrganizationBusinessLevelEnabled(String businessLevelCode) {
        return organizations.isOrganizationBusinessLevelEnabled(businessLevelCode);
    }

    @Override
    public List<OrganizationBusinessLevelView> listEnabledOrganizationBusinessLevels() {
        return organizations.listEnabledOrganizationBusinessLevels();
    }

    @Override
    public List<Role> listRoles(boolean enabledOnly) {
        return directory.listRoles(enabledOnly);
    }

    @Override
    public List<Role> listUserRoles(String userId) {
        return directory.listUserRoles(userId);
    }

    @Override
    public void replaceUserRoles(List<String> userIds, List<String> roleIds) {
        memberships.replaceUserRoles(userIds, roleIds);
    }

    @Override
    public List<IdentityGroup> listUserGroups(String userId) {
        return directory.listUserGroups(userId);
    }

    @Override
    public List<IdentityUser> findGroupUsers(String idOrCode) {
        return groups.findGroupUsers(idOrCode);
    }

    @Override
    public void replaceGroupMembers(String groupId, List<String> userIds) {
        memberships.replaceGroupMembers(groupId, userIds);
    }
}
