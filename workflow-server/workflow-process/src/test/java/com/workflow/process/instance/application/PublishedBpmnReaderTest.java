package com.workflow.process.instance.application;

import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.Model;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublishedBpmnReaderTest {
    private final RepositoryService repository = mock(RepositoryService.class, RETURNS_DEEP_STUBS);
    private final ProcessPublishedSnapshotService snapshots = mock(ProcessPublishedSnapshotService.class);
    private final PublishedBpmnReader reader = new PublishedBpmnReader(repository, snapshots);
    private final ProcessDefinition definition = mock(ProcessDefinition.class);

    private void deployedDefinition() {
        when(definition.getId()).thenReturn("expense:1:old");
        when(definition.getResourceName()).thenReturn("expense.bpmn");
        when(definition.getDeploymentId()).thenReturn("deploy-old");
        when(repository.createProcessDefinitionQuery().processDefinitionId("expense:1:old").singleResult())
                .thenReturn(definition);
        when(repository.createDeploymentQuery().deploymentId("deploy-old").singleResult().getId())
                .thenReturn("deploy-old");
        when(repository.getModel("expense:1:old")).thenReturn(null);
    }

    @Test
    void modelSourceTakesPrecedenceAndPreservesRawDiagramXml() {
        deployedDefinition();
        Model model = mock(Model.class);
        when(model.getId()).thenReturn("model-old");
        when(repository.getModel("expense:1:old")).thenReturn(model);
        String xml = "<definitions><!-- 原始 DI -->\n<bpmndi:BPMNDiagram/></definitions>";
        when(repository.getModelEditorSource("model-old")).thenReturn(xml.getBytes(StandardCharsets.UTF_8));
        assertEquals(xml, reader.read("expense:1:old"));
        verify(repository, never()).getResourceAsStream(anyString(), anyString());
        verifyNoInteractions(snapshots);
    }

    @Test
    void deploymentFallbackClosesStreamAndPreservesUtf8() throws Exception {
        deployedDefinition();
        var stream = spy(new ByteArrayInputStream("<definitions>审批图</definitions>".getBytes(StandardCharsets.UTF_8)));
        when(repository.getResourceAsStream("deploy-old", "expense.bpmn")).thenReturn(stream);
        assertEquals("<definitions>审批图</definitions>", reader.read(definition));
        verify(stream).close();
        verifyNoInteractions(snapshots);
    }

    @Test
    void failedDeploymentReadClosesStreamAndOnlyUsesExactPublishedVersion() throws Exception {
        deployedDefinition();
        InputStream broken = mock(InputStream.class);
        when(broken.readAllBytes()).thenThrow(new IOException("resource unavailable"));
        when(repository.getResourceAsStream("deploy-old", "expense.bpmn")).thenReturn(broken);
        var old = new ProcessVersionHistory(); old.setBpmnXml("<old-version/>");
        when(snapshots.getVersionByProcessDefinitionId("expense:1:old")).thenReturn(old);
        assertEquals("<old-version/>", reader.read("expense:1:old"));
        verify(broken).close();
        verify(snapshots).getVersionByProcessDefinitionId("expense:1:old");
        verifyNoMoreInteractions(snapshots);
    }

    @Test
    void missingDefinitionDoesNotSelectLatestPublishedOrDraftVersion() {
        when(repository.createProcessDefinitionQuery().processDefinitionId("missing").singleResult()).thenReturn(null);
        assertNull(reader.read("missing"));
        verifyNoInteractions(snapshots);
    }
}
