package com.workflow.service.entity;

import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityForm;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.publish.ProcessPublishedSnapshotService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 实体表单解析服务测试。
 *
 * <p>被测对象：{@link EntityFormResolveService}，覆盖新建数据时使用首个可达用户任务绑定的表单、
 * 首个用户任务解析可穿越网关等场景。
 */
class EntityFormResolveServiceTest {

    /** 测试新建数据使用首个可达用户任务绑定的表单：验证解析返回的表单 id 与首个用户任务绑定一致 */
    @Test
    void newDataUsesFirstReachableUserTaskForm() {
        EntityDefinitionMapper entityDefinitionMapper = mock(EntityDefinitionMapper.class);
        ProcessDefinitionConfigMapper processConfigMapper = mock(ProcessDefinitionConfigMapper.class);
        ProcessPublishedSnapshotService snapshotService =
                mock(ProcessPublishedSnapshotService.class);
        EntityFormRuntimeService formService = mock(EntityFormRuntimeService.class);
        EntityFormResolveService service = new EntityFormResolveService(
                entityDefinitionMapper,
                processConfigMapper,
                snapshotService,
                formService,
                mock(RuntimeService.class),
                mock(TaskService.class));

        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setLifecycleMode(EntityDefinition.LifecycleMode.WORKFLOW);
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        entity.setProcessDefinitionId("process-1");
        when(entityDefinitionMapper.findByEntityCode("expense")).thenReturn(Optional.of(entity));

        ProcessDefinitionConfig process = new ProcessDefinitionConfig();
        process.setId("process-1");
        process.setProcessKey("expense-flow");
        process.setBpmnXml(bpmn());
        when(processConfigMapper.selectById("process-1")).thenReturn(process);

        ProcessNodeForm binding = new ProcessNodeForm();
        binding.setFormId("form-first");
        binding.setFormReleaseId("release-2");
        binding.setFormReleaseVersion(2);
        when(snapshotService.getNodeForms("expense-flow", "Task_First"))
                .thenReturn(List.of(binding));

        EntityForm form = new EntityForm();
        form.setId("form-first");
        when(formService.getByBinding(binding)).thenReturn(form);

        assertEquals("form-first", service.resolveFormForNewData("expense").getId());
    }

    /** 测试首个用户任务解析可穿越网关：验证解析结果为网关之后的 Task_First */
    @Test
    void firstUserTaskParserTraversesGateway() {
        EntityFormResolveService service = new EntityFormResolveService(
                mock(EntityDefinitionMapper.class),
                mock(ProcessDefinitionConfigMapper.class),
                mock(ProcessPublishedSnapshotService.class),
                mock(EntityFormRuntimeService.class),
                mock(RuntimeService.class),
                mock(TaskService.class));

        assertEquals("Task_First", service.resolveFirstUserTaskId(bpmn()));
    }

    /** 构造含网关与两个用户任务的测试 BPMN XML */
    private String bpmn() {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<process id=\"test\">"
                + "<startEvent id=\"Start\"/>"
                + "<exclusiveGateway id=\"Gateway\"/>"
                + "<userTask id=\"Task_First\"/>"
                + "<userTask id=\"Task_Later\"/>"
                + "<sequenceFlow id=\"F1\" sourceRef=\"Start\" targetRef=\"Gateway\"/>"
                + "<sequenceFlow id=\"F2\" sourceRef=\"Gateway\" targetRef=\"Task_First\"/>"
                + "<sequenceFlow id=\"F3\" sourceRef=\"Task_First\" targetRef=\"Task_Later\"/>"
                + "</process>"
                + "</definitions>";
    }
}
