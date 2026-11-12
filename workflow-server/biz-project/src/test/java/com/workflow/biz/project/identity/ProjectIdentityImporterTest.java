package com.workflow.biz.project.identity;

import com.workflow.contracts.identity.model.*;
import com.workflow.contracts.identity.port.IdentityWritePort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectIdentityImporterTest {
    @Test
    void businessAdapterMapsFetchedDataToPublicContract() {
        var port = mock(IdentityWritePort.class);
        var importer = new ProjectIdentityImporter(port);
        importer.saveUser(new ProjectIdentityImporter.DirectoryUser("E1", 7, "alice", "张三",
                "a@example.com", null, "external-org", "external-dept", true));
        var input = ArgumentCaptor.forClass(UserSaveModel.class);
        verify(port).saveUser(input.capture());
        assertEquals(new ExternalIdentityKey("project-directory", "E1"), input.getValue().key());
        assertEquals(7L, input.getValue().sourceVersion());
        assertEquals("external-org", input.getValue().base().organizationExternalId());
        assertTrue(input.getValue().extensions().isEmpty());
        assertNull(input.getValue().rawDataJson());
    }

    @Test void passesOriginalBeforeMappingWithoutLosingExtraFieldsOrNumbers() {
        var port = mock(IdentityWritePort.class);
        var importer = new ProjectIdentityImporter(port);
        String original = "{\"extra\":9223372036854775807123}";
        importer.saveOrganization(new ProjectIdentityImporter.DirectoryOrganization("O1", 1, "HQ", "总部",
                OrganizationUnitType.ORGANIZATION, null, true), original);
        var input = ArgumentCaptor.forClass(OrganizationSaveModel.class);
        verify(port).saveOrganization(input.capture());
        assertEquals(original, input.getValue().rawDataJson());
    }
}
