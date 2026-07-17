package com.workflow.service;

import com.workflow.entity.EntityForm;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.ProcessNodeFormMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessNodeFormServiceMultiFormTest {

    @Test
    void getListByNodeIdReturnsAllNodeFormsWithFormInfo() {
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        ProcessNodeFormService service = new ProcessNodeFormService(nodeFormMapper, formMapper);

        ProcessNodeForm baseForm = nodeForm("bind-1", "base-form");
        ProcessNodeForm detailForm = nodeForm("bind-2", "detail-form");

        EntityForm baseEntityForm = entityForm("base-form", "基础表单");
        EntityForm detailEntityForm = entityForm("detail-form", "明细表单");

        when(nodeFormMapper.selectListByNodeId("process-1", "task-1")).thenReturn(List.of(baseForm, detailForm));
        when(formMapper.selectById("base-form")).thenReturn(baseEntityForm);
        when(formMapper.selectById("detail-form")).thenReturn(detailEntityForm);

        List<ProcessNodeForm> result = service.getListByNodeId("process-1", "task-1");

        assertEquals(2, result.size());
        assertEquals("基础表单", result.get(0).getForm().getFormName());
        assertEquals("明细表单", result.get(1).getForm().getFormName());
    }

    private static ProcessNodeForm nodeForm(String id, String formId) {
        ProcessNodeForm nodeForm = new ProcessNodeForm();
        nodeForm.setId(id);
        nodeForm.setProcessConfigId("process-1");
        nodeForm.setNodeId("task-1");
        nodeForm.setFormId(formId);
        return nodeForm;
    }

    private static EntityForm entityForm(String id, String name) {
        EntityForm form = new EntityForm();
        form.setId(id);
        form.setFormName(name);
        return form;
    }
}
