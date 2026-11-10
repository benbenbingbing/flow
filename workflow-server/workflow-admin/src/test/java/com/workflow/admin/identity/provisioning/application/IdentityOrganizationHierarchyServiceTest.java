package com.workflow.admin.identity.provisioning.application;

import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.contracts.identity.model.ExternalIdentityKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 驱动返回实际变更行数时，同值结构写入返回 0 仍表示关联处理成功。 */
class IdentityOrganizationHierarchyServiceTest {
    @Test
    void unchangedHierarchyDoesNotDependOnMatchedRowCount() {
        var store = mock(IdentitySourceStore.class);
        var persistence = mock(IdentityPersistenceMapper.class);
        var organizations = mock(SysOrganizationMapper.class);
        when(organizations.updateProvisionedHierarchy(any())).thenReturn(0);
        var hierarchy = new IdentityOrganizationHierarchyService(store, persistence, organizations);
        var root = new SysOrganization();
        root.setId("root");
        root.setParentId("0");
        root.setType("org");
        root.setLevel(0);
        root.setPath("/0/root/");

        assertDoesNotThrow(() -> hierarchy.reconcile(new ExternalIdentityKey("erp", "root"), null, root));
        verify(organizations).updateProvisionedHierarchy(root);
        verify(organizations).selectChildren("root");
    }
}
