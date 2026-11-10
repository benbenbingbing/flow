package com.workflow.admin.identity.provisioning.application;

import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.ExternalIdentityKey;
import com.workflow.contracts.identity.model.IdentityObjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 来源组织的待关联父级和派生树结构，所有入口均由 IdentityWriteService 在目录锁及同一事务内调用。 */
@Component
@RequiredArgsConstructor
public class IdentityOrganizationHierarchyService {
    private final IdentitySourceStore store;
    private final IdentityPersistenceMapper persistence;
    private final SysOrganizationMapper organizations;

    /**
     * 校验来源父链并设置本地父级及就绪状态，父级缺失时允许组织先保存。
     *
     * @param organization 本次待保存组织，父链完整时交由原组织服务计算路径
     * @param key 同来源外部身份，参与循环检测及后续父子定位
     * @param parentExternalId null 表示根节点，其他值须为非空且长度不超过 255 的稳定外部 ID
     * @throws IdentityWriteException 父链成环、父级已退休或已知父子类型非法
     */
    public void prepare(SysOrganization organization, ExternalIdentityKey key, String parentExternalId) {
        if (parentExternalId == null) {
            organization.setParentId("0");
            organization.setHierarchyReady(true);
            return;
        }
        if (parentExternalId.isBlank() || parentExternalId.length() > 255 || parentExternalId.indexOf(0) >= 0) {
            throw new IdentityWriteException("INVALID_MODEL", "parentExternalId", "父级外部 ID 非法");
        }
        String parentKey = parentKey(key, parentExternalId);
        Set<String> visited = new HashSet<>();
        visited.add(IdentitySourceStore.hash(IdentityObjectType.ORGANIZATION, key));
        // 沿持久化来源引用检查，即使祖先还未入库也能在后续来数闭环时拒绝跨批次循环。
        String cursor = parentKey;
        while (cursor != null) {
            if (!visited.add(cursor)) invalid("组织来源父链存在环");
            Map<String, Object> ancestor = persistence.findBinding(cursor);
            cursor = ancestor == null ? null : (String) ancestor.get("parent_source_key");
        }
        Map<String, Object> binding = persistence.findBinding(parentKey);
        SysOrganization parent = null;
        if (binding != null) {
            parent = organizations.selectById((String) binding.get("local_id"));
            if (!"ACTIVE".equals(binding.get("binding_status")) || parent == null) {
                throw new IdentityWriteException("REFERENCE_NOT_FOUND", "parentExternalId", "引用的来源父级已退休或缺失");
            }
            validateTypes(organization, parent);
        }
        organization.setParentId(parent == null ? null : parent.getId());
        organization.setHierarchyReady(parent != null && parent.isHierarchyReady());
    }

    /**
     * 保存本次来源父级引用，并沿受影响子树补关联及重算路径、层级，包括已有本地下级。
     * 同事务失败将回滚基础资料、来源版本及所有结构变更，调用方无需再次提交旧子级快照。
     *
     * @param key 本次已保存的来源组织身份，用于定位等待它的子级
     * @param parentExternalId 本次完整快照的父级引用，null 表示根节点并清除旧引用
     * @param organization 已保存且已计算当前结构的组织，作为子树遍历起点
     * @throws IdentityWriteException 后到父级与已有子级类型冲突、本地父链成环或派生路径超长
     */
    public void reconcile(ExternalIdentityKey key, String parentExternalId, SysOrganization organization) {
        persistence.updateOrganizationParent(IdentitySourceStore.hash(IdentityObjectType.ORGANIZATION, key),
                parentExternalId, parentKey(key, parentExternalId));
        // 普通 updateById 保持忽略 NULL 的兼容语义，来源入口在此显式清除失效的父级、路径和层级。
        // 目录锁内组织已经保存且不会被并发删除，驱动对同值更新可能返回 0，不能据此判为组织缺失。
        organizations.updateProvisionedHierarchy(organization);
        var queue = new ArrayDeque<SysOrganization>();
        Set<String> visited = new HashSet<>();
        queue.add(organization);
        while (!queue.isEmpty()) {
            SysOrganization parent = queue.removeFirst();
            if (!visited.add(parent.getId())) invalid("组织本地父链存在环");
            Map<String, SysOrganization> children = new LinkedHashMap<>();
            for (SysOrganization child : organizations.selectChildren(parent.getId())) children.put(child.getId(), child);
            Map<String, Object> binding = store.findLocal(IdentityObjectType.ORGANIZATION, parent.getId());
            if (binding != null) {
                for (String id : persistence.selectOrganizationChildren((String) binding.get("source_key"))) {
                    SysOrganization child = organizations.selectById(id);
                    if (child != null) children.put(id, child);
                }
            }
            for (SysOrganization child : children.values()) {
                validateTypes(child, parent);
                child.setParentId(parent.getId());
                child.setHierarchyReady(parent.isHierarchyReady());
                child.setLevel(parent.isHierarchyReady() ? parent.getLevel() + 1 : null);
                child.setPath(parent.isHierarchyReady() ? parent.getPath() + child.getId() + "/" : null);
                if (child.getPath() != null && child.getPath().length() > 500) invalid("组织路径超过最大长度");
                organizations.updateProvisionedHierarchy(child);
                queue.addLast(child);
            }
        }
    }

    /** 同来源哈希同时编码 sourceCode 和外部 ID，不能使用数据库大小写不敏感的文本相等判断替代。 */
    private String parentKey(ExternalIdentityKey key, String parentExternalId) {
        return parentExternalId == null ? null : IdentitySourceStore.hash(IdentityObjectType.ORGANIZATION,
                new ExternalIdentityKey(key.sourceCode(), parentExternalId));
    }

    /** 后到父级也须校验已有子级的类型，避免把已接收的组织挂到部门下面。 */
    private void validateTypes(SysOrganization child, SysOrganization parent) {
        if ("org".equals(child.getType()) && !"org".equals(parent.getType())) invalid("组织不能放在部门下");
    }

    private void invalid(String message) {
        throw new IdentityWriteException("INVALID_ORG_HIERARCHY", "parentExternalId", message);
    }
}
