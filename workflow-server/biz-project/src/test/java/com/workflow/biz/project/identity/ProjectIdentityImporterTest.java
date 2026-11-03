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
    }
}
