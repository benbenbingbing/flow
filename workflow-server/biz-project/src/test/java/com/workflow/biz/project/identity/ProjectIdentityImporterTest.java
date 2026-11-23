package com.workflow.biz.project.identity;

import com.workflow.port.system.SysUserPort;
import com.workflow.port.system.SysOrganizationPort;

import com.workflow.port.system.identity.model.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectIdentityImporterTest {
    @Test
    void businessAdapterMapsFetchedDataToPublicContract() {
        var users = mock(SysUserPort.class);
        var organizations = mock(SysOrganizationPort.class);
        var importer = new ProjectIdentityImporter(users, organizations);
        importer.saveUser(new ProjectIdentityImporter.DirectoryUser("E1", 7, "alice", "张三",
                "a@example.com", null, "external-org", "external-dept", true));
        var input = ArgumentCaptor.forClass(UserSaveModel.class);
        verify(users).saveUser(input.capture());
        assertEquals(new ExternalIdentityKey("project-directory", "E1"), input.getValue().key());
        assertEquals(7L, input.getValue().sourceVersion());
        assertEquals("external-org", input.getValue().base().organizationExternalId());
        assertTrue(input.getValue().extensions().isEmpty());
        assertNull(input.getValue().rawDataJson());
        assertNull(input.getValue().mainFields());
    }

    @Test void passesOriginalBeforeMappingWithoutLosingExtraFieldsOrNumbers() {
        var users = mock(SysUserPort.class);
        var organizations = mock(SysOrganizationPort.class);
        var importer = new ProjectIdentityImporter(users, organizations);
        String original = "{\"extra\":9223372036854775807123}";
        importer.saveOrganization(new ProjectIdentityImporter.DirectoryOrganization("O1", 1, "HQ", "总部",
                OrganizationUnitType.ORGANIZATION, null, true), original);
        var input = ArgumentCaptor.forClass(OrganizationSaveModel.class);
        verify(organizations).saveOrganization(input.capture());
        assertEquals(original, input.getValue().rawDataJson());
        assertNull(input.getValue().mainFields());
    }

    @Test
    void standardSyncPassesMappedMainFieldsFromBothSnapshots() {
        var users = mock(SysUserPort.class);
        var organizations = mock(SysOrganizationPort.class);
        var importer = new ProjectIdentityImporter(users, organizations);
        var userFields = new IdentityMainFields("0007", "员工类别", "成本中心", null, "华东");
        var orgFields = new IdentityMainFields("0012", "分公司", null, "区域编码", "华南");
        String original = "{\"region\":\"华南\"}";
        importer.saveUser(new ProjectIdentityImporter.DirectoryUser("E1", 1, "alice", "张三",
                null, null, null, null, true, userFields));
        importer.saveOrganization(new ProjectIdentityImporter.DirectoryOrganization("O1", 1, "HQ", "总部",
                OrganizationUnitType.ORGANIZATION, null, true, orgFields), original);

        var user = ArgumentCaptor.forClass(UserSaveModel.class);
        var org = ArgumentCaptor.forClass(OrganizationSaveModel.class);
        verify(users).saveUser(user.capture());
        verify(organizations).saveOrganization(org.capture());
        assertEquals(userFields, user.getValue().mainFields());
        assertEquals(orgFields, org.getValue().mainFields());
        assertEquals(original, org.getValue().rawDataJson());
    }

    @Test void passesExplicitMainTableFieldsForBothIdentityTypes() {
        var users = mock(SysUserPort.class);
        var organizations = mock(SysOrganizationPort.class);
        var importer = new ProjectIdentityImporter(users, organizations);
        var fields = IdentityMainFields.builder().ext1("0007").ext5("华东").build();
        importer.saveUser(new ProjectIdentityImporter.DirectoryUser("E1", 1, "alice", "张三",
                null, null, null, null, true), "{}", fields);
        importer.saveOrganization(new ProjectIdentityImporter.DirectoryOrganization("O1", 1, "HQ", "总部",
                OrganizationUnitType.ORGANIZATION, null, true), "{}", fields);
        var user = ArgumentCaptor.forClass(UserSaveModel.class);
        var org = ArgumentCaptor.forClass(OrganizationSaveModel.class);
        verify(users).saveUser(user.capture()); verify(organizations).saveOrganization(org.capture());
        assertEquals(fields, user.getValue().mainFields());
        assertEquals(fields, org.getValue().mainFields());
    }
}
