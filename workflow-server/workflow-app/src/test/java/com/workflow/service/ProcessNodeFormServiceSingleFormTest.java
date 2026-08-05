package com.workflow.service;

import com.workflow.process.form.application.ProcessNodeFormService;

import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.process.form.infrastructure.persistence.mapper.ProcessNodeFormMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流程节点单表单绑定服务测试。
 */
class ProcessNodeFormServiceSingleFormTest {

    /**
     * 列表兼容接口最多返回当前节点的一条表单绑定。
     */
    @Test
    void getListByNodeIdReturnsSingleNodeFormWithFormInfo() {
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        ProcessNodeFormService service = new ProcessNodeFormService(nodeFormMapper, formMapper);

        ProcessNodeForm baseForm = nodeForm("bind-1", "base-form");
        EntityForm baseEntityForm = entityForm("base-form", "基础表单");

        when(nodeFormMapper.selectByNodeId("process-1", "task-1")).thenReturn(baseForm);
        when(formMapper.selectById("base-form")).thenReturn(baseEntityForm);

        List<ProcessNodeForm> result = service.getListByNodeId("process-1", "task-1");

        assertEquals(1, result.size());
        assertEquals("基础表单", result.get(0).getForm().getFormName());
    }

    /** 构造一个绑定指定表单的节点表单对象 */
    private static ProcessNodeForm nodeForm(String id, String formId) {
        ProcessNodeForm nodeForm = new ProcessNodeForm();
        nodeForm.setId(id);
        nodeForm.setProcessConfigId("process-1");
        nodeForm.setNodeId("task-1");
        nodeForm.setFormId(formId);
        return nodeForm;
    }

    /** 构造一个带 id 与名称的实体表单 */
    private static EntityForm entityForm(String id, String name) {
        EntityForm form = new EntityForm();
        form.setId(id);
        form.setFormName(name);
        return form;
    }
}
