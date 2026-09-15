package com.workflow.process.runtime;

import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.form.application.EntityFormRuntimeService;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.instance.api.response.ProcessProgressDTO;
import com.workflow.process.instance.application.ProcessProgressRuntimeService;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.process.task.application.LocalAddSignTaskAccessService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.instance.application.WorkflowReservedVariables;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 使用真实 Flowable 历史变量作用域，验证自动跳过后仍能查看进度和发布表单。 */
class ProcessProgressAutoSkipIntegrationTest {
    private ProcessEngine engine;
    private ProcessProgressRuntimeService service;

    @BeforeEach
    void setUp() {
        var configuration = ProcessEngineConfiguration
                .createStandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:progress_skip_" + UUID.randomUUID());
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setHistory("full");
        engine = configuration.buildProcessEngine();
        engine.getRepositoryService().createDeployment()
                .addString("progress-skip.bpmn20.xml", bpmn()).deploy();
        service = progressService();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    /**
     * 同名变量可合法存在于流程、任务和执行分支中；跳过任务没有任务级操作名称，
     * 此时只能兼容读取流程根变量，不能读取其他任务或执行分支的值。
     */
    @ParameterizedTest
    @CsvSource({"true, false", "false, false", "false, true", "true, true"})
    void viewAfterAutoSkipUsesOnlyRootVariableForLegacyFallback(
            boolean legacyRootLabel, boolean executionLocalLabel) {
        var variables = new HashMap<String, Object>();
        WorkflowReservedVariables.enableNativeSkipExpressions(variables);
        variables.put("entityCode", "all_entity");
        variables.put("entityDataId", "data-1");
        if (legacyRootLabel) {
            variables.put("actionLabel", "历史流程操作");
        }
        String instanceId = engine.getRuntimeService()
                .startProcessInstanceByKey("progress_skip", variables).getId();
        var first = engine.getTaskService().createTaskQuery()
                .processInstanceId(instanceId).singleResult();
        engine.getTaskService().setVariableLocal(first.getId(), "actionLabel", "首节点操作");
        if (executionLocalLabel) {
            engine.getRuntimeService().setVariableLocal(
                    first.getExecutionId(), "actionLabel", "执行分支操作");
        }
        engine.getTaskService().complete(first.getId());

        // 原生跳过仍会生成已完成历史任务，必须走真实引擎查询才能覆盖多作用域问题。
        assertNotNull(engine.getHistoryService().createHistoricTaskInstanceQuery()
                .processInstanceId(instanceId).taskDefinitionKey("skipped").finished().singleResult());
        assertEquals(1 + (legacyRootLabel ? 1 : 0) + (executionLocalLabel ? 1 : 0),
                engine.getHistoryService().createHistoricVariableInstanceQuery()
                        .processInstanceId(instanceId).variableName("actionLabel").count());
        String expectedFallback = legacyRootLabel ? "历史流程操作" : null;
        ProcessProgressDTO running = service.getProcessProgress(instanceId);
        assertEquals("RUNNING", running.getStatus());
        assertNodeLabel(running, "first", "首节点操作");
        assertNodeLabel(running, "skipped", expectedFallback);
        assertFormAndData(running);

        var last = engine.getTaskService().createTaskQuery()
                .processInstanceId(instanceId).singleResult();
        engine.getTaskService().setVariableLocal(last.getId(), "actionLabel", "末节点操作");
        engine.getTaskService().complete(last.getId());

        // 最后一个节点同样自动跳过，覆盖截图中的已完成流程查看入口。
        ProcessProgressDTO completed = service.getProcessProgress(instanceId);
        assertEquals("COMPLETED", completed.getStatus());
        assertNodeLabel(completed, "first", "首节点操作");
        assertNodeLabel(completed, "last", "末节点操作");
        assertNodeLabel(completed, "skipped-end", expectedFallback);
        assertFormAndData(completed);
    }

    /** 审批历史与节点悬停信息使用同一作用域规则，避免只修复其中一条查询。 */
    private void assertNodeLabel(ProcessProgressDTO progress, String nodeId, String expected) {
        var history = progress.getNodeHistory().stream()
                .filter(node -> nodeId.equals(node.getNodeId())).findFirst().orElseThrow();
        assertEquals(expected, history.getActionLabel());
        assertEquals(expected, progress.getNodeAssigneeMap().get(nodeId).getActionLabel());
    }

    private void assertFormAndData(ProcessProgressDTO progress) {
        assertEquals("测试自动跳过", progress.getEntityData().get("name"));
        assertNotNull(progress.getFormConfig());
        assertEquals("查看表单", progress.getFormConfig().getFormName());
    }

    /** 引擎查询全部真实执行；实体与发布快照固定为可查看表单，隔离业务数据库依赖。 */
    private ProcessProgressRuntimeService progressService() {
        var entityService = mock(EntityDataDynamicService.class);
        var data = new EntityDataDTO();
        data.setName("测试自动跳过");
        when(entityService.findById("all_entity", "data-1")).thenReturn(data);
        var entityMapper = mock(EntityDefinitionMapper.class);
        var entity = new EntityDefinition();
        entity.setId("entity-1");
        when(entityMapper.findByEntityCode("all_entity")).thenReturn(Optional.of(entity));
        var snapshots = mock(ProcessPublishedSnapshotService.class);
        var published = new ProcessVersionHistory();
        published.setId("published-1");
        var binding = new ProcessNodeForm();
        binding.setFormId("form-1");
        when(snapshots.getNodeFormsContextByProcessDefinitionId(anyString(), anyString()))
                .thenReturn(new ProcessPublishedSnapshotService.PublishedNodeForms(published, List.of(binding)));
        var forms = mock(EntityFormRuntimeService.class);
        var form = new EntityForm();
        form.setId("form-1");
        form.setFormName("查看表单");
        when(forms.getByBinding(any(), any())).thenReturn(form);
        return new ProcessProgressRuntimeService(
                engine.getRuntimeService(), engine.getHistoryService(), engine.getRepositoryService(),
                engine.getTaskService(), mock(SysUserService.class), entityService, forms, entityMapper,
                mock(ProcessTaskMapper.class), mock(SysGroupMapper.class), mock(SysUserGroupMapper.class),
                mock(SysUserMapper.class), mock(ProcessOperationLogMapper.class), snapshots,
                mock(LocalAddSignTaskAccessService.class));
    }

    private String bpmn() {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://workflow.test/process">
                  <process id="progress_skip" name="自动跳过查看测试" isExecutable="true">
                    <startEvent id="start" />
                    <userTask id="first" name="首节点" flowable:assignee="admin" />
                    <userTask id="skipped" name="自动跳过" flowable:skipExpression="${true}" />
                    <userTask id="last" name="末节点" flowable:assignee="admin" />
                    <userTask id="skipped-end" name="末尾自动跳过" flowable:skipExpression="${true}" />
                    <endEvent id="end" />
                    <sequenceFlow id="f1" sourceRef="start" targetRef="first" />
                    <sequenceFlow id="f2" sourceRef="first" targetRef="skipped" />
                    <sequenceFlow id="f3" sourceRef="skipped" targetRef="last" />
                    <sequenceFlow id="f4" sourceRef="last" targetRef="skipped-end" />
                    <sequenceFlow id="f5" sourceRef="skipped-end" targetRef="end" />
                  </process>
                </definitions>
                """;
    }
}
