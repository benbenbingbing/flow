package com.workflow.process.publish;

import com.workflow.process.publish.application.ProcessUiReleaseBindingService;

import com.workflow.contracts.ui.runtime.UiPublishedFormReference;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.publish.infrastructure.persistence.record.ProcessUiReleaseBinding;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.publish.infrastructure.persistence.mapper.ProcessUiReleaseBindingMapper;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程 UI 发布绑定递归生成与幂等回填测试。
 */
class ProcessUiReleaseBindingServiceTest {

    @Test
    void replaceBindingsExpandsRootAndNestedFormsRecursively() {
        ProcessUiReleaseBindingMapper mapper =
                mock(ProcessUiReleaseBindingMapper.class);
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        ProcessUiReleaseBindingService service =
                service(mapper, releaseService);
        ProcessVersionHistory history = history();
        ProcessNodeForm root = nodeForm(
                "task-root",
                "根审批",
                "form-root",
                "release-root",
                1);

        when(releaseService.childFormReferences(
                "form-root",
                "release-root",
                1)).thenReturn(List.of(reference(
                        "form-child",
                        "release-child",
                        2)));
        when(releaseService.childFormReferences(
                "form-child",
                "release-child",
                2)).thenReturn(List.of(reference(
                        "form-grandchild",
                        "release-grandchild",
                        3)));
        when(releaseService.childFormReferences(
                "form-grandchild",
                "release-grandchild",
                3)).thenReturn(List.of());

        int inserted = service.replaceBindings(history, List.of(root));

        ArgumentCaptor<ProcessUiReleaseBinding> captor =
                ArgumentCaptor.forClass(ProcessUiReleaseBinding.class);
        assertEquals(3, inserted);
        verify(mapper).deleteByHistoryId("history-1");
        verify(mapper, times(3)).insert(captor.capture());
        List<ProcessUiReleaseBinding> bindings = captor.getAllValues();
        assertEquals(
                List.of(
                        "form-root",
                        "form-child",
                        "form-grandchild"),
                bindings.stream()
                        .map(ProcessUiReleaseBinding::getConfigId)
                        .toList());
        assertEquals("task-root", bindings.get(0).getNodeId());
        assertTrue(bindings.get(1).getNodeId()
                .startsWith("task-root#sub:"));
        assertTrue(bindings.get(2).getNodeId()
                .startsWith("task-root#sub:"));
        assertFalse(bindings.get(1).getNodeId()
                .equals(bindings.get(2).getNodeId()));
    }

    @Test
    void replaceBindingsLimitsNestedExpansionToEightLevels() {
        ProcessUiReleaseBindingMapper mapper =
                mock(ProcessUiReleaseBindingMapper.class);
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        ProcessUiReleaseBindingService service =
                service(mapper, releaseService);
        ProcessNodeForm root = nodeForm(
                "task-depth",
                "深层审批",
                "form-0",
                "release-0",
                1);
        for (int index = 0; index < 10; index++) {
            when(releaseService.childFormReferences(
                    "form-" + index,
                    "release-" + index,
                    index + 1)).thenReturn(List.of(reference(
                            "form-" + (index + 1),
                            "release-" + (index + 1),
                            index + 2)));
        }

        int inserted = service.replaceBindings(
                history(),
                List.of(root));

        assertEquals(9, inserted);
        verify(mapper, times(9)).insert(
                org.mockito.ArgumentMatchers.any(
                        ProcessUiReleaseBinding.class));
        verify(releaseService, never()).childFormReferences(
                "form-8",
                "release-8",
                9);
    }

    @Test
    void replaceBindingsStopsWhenReleaseCycleRepeats() {
        ProcessUiReleaseBindingMapper mapper =
                mock(ProcessUiReleaseBindingMapper.class);
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        ProcessUiReleaseBindingService service =
                service(mapper, releaseService);
        ProcessNodeForm root = nodeForm(
                "task-cycle",
                "循环审批",
                "form-a",
                "release-a",
                1);

        when(releaseService.childFormReferences(
                "form-a",
                "release-a",
                1)).thenReturn(List.of(reference(
                        "form-b",
                        "release-b",
                        2)));
        when(releaseService.childFormReferences(
                "form-b",
                "release-b",
                2)).thenReturn(List.of(reference(
                        "form-a",
                        "release-a",
                        1)));

        int inserted = service.replaceBindings(
                history(),
                List.of(root));

        assertEquals(3, inserted);
        verify(releaseService, times(1)).childFormReferences(
                "form-a",
                "release-a",
                1);
        verify(releaseService, times(1)).childFormReferences(
                "form-b",
                "release-b",
                2);
    }

    private static ProcessUiReleaseBindingService service(
            ProcessUiReleaseBindingMapper mapper,
            UiConfigReleaseService releaseService) {
        return new ProcessUiReleaseBindingService(
                mapper,
                releaseService);
    }

    private static ProcessVersionHistory history() {
        ProcessVersionHistory history = new ProcessVersionHistory();
        history.setId("history-1");
        history.setProcessConfigId("process-1");
        history.setProcessKey("expense_flow");
        history.setProcessName("费用流程");
        history.setVersion(3);
        history.setDeploymentId("deployment-3");
        return history;
    }

    private static ProcessNodeForm nodeForm(
            String nodeId,
            String nodeName,
            String formId,
            String releaseId,
            int releaseVersion) {
        ProcessNodeForm nodeForm = new ProcessNodeForm();
        nodeForm.setNodeId(nodeId);
        nodeForm.setNodeName(nodeName);
        nodeForm.setFormId(formId);
        nodeForm.setFormReleaseId(releaseId);
        nodeForm.setFormReleaseVersion(releaseVersion);
        return nodeForm;
    }

    private static UiPublishedFormReference reference(
            String formId,
            String releaseId,
            int releaseVersion) {
        return new UiPublishedFormReference(
                formId,
                releaseId,
                releaseVersion);
    }

}
