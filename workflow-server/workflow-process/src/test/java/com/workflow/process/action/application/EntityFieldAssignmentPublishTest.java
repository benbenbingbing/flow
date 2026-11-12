package com.workflow.process.action.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import org.junit.jupiter.api.*;
import org.springframework.context.support.StaticApplicationContext;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实配置校验器验证发布边界，包括草稿外部导入和并行写入竞争。 */
class EntityFieldAssignmentPublishTest {
    final ObjectMapper json = new ObjectMapper();
    final EntityPublishedSnapshotService snapshots = mock(EntityPublishedSnapshotService.class);
    final EntityCodeCatalogPort entities = mock(EntityCodeCatalogPort.class);
    final ProcessDefinitionConfigMapper configs = mock(ProcessDefinitionConfigMapper.class);
    final FlowActionService actions = mock(FlowActionService.class);
    final StaticApplicationContext context = new StaticApplicationContext();
    final ProcessDefinitionConfig config = new ProcessDefinitionConfig();
    FlowActionPublishValidator publisher;
    FlowActionConfigurationValidator validator;

    @BeforeEach void setup() {
        var snapshot = new EntityPublishedSnapshot();
        snapshot.setFields(List.of(EntityFieldAssignmentFlowActionHandlerTest.field("flag", EntityField.FieldType.BOOLEAN),
                EntityFieldAssignmentFlowActionHandlerTest.field("other", EntityField.FieldType.BOOLEAN)));
        when(entities.findEntityCodeByProcessDefinitionId("process")).thenReturn("expense");
        when(snapshots.getLatestByEntityCode("expense")).thenReturn(snapshot);
        var handler = new EntityFieldAssignmentFlowActionHandler(entities, snapshots,
                new EntityFieldValidationRuleService(json), mock(SysDictItemService.class), mock(EntityMutationPort.class), json);
        context.getBeanFactory().registerSingleton(EntityFieldAssignmentFlowActionHandler.BEAN_NAME, handler);
        validator = new FlowActionConfigurationValidator(configs, context, new FlowActionTimingCatalog(List.of()), json,
                mock(FlowActionFailureStrategyCatalog.class));
        publisher = new FlowActionPublishValidator(actions, validator, handler, configs, json);
        when(configs.selectById("process")).thenReturn(config);
        config.setBpmnXml(graph("parallelGateway"));
    }
    @AfterEach void close() { context.close(); }

    @Test void saveAndPublishBothRejectMissingFieldsAndNonRollbackPolicy() {
        var action = action("left", "missing", "true");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(action));
        when(actions.findDraftActions("process")).thenReturn(List.of(action));
        assertThrows(IllegalArgumentException.class, () -> publisher.validate("process"));
        action.setParamsJson(action("left", "flag", "true").getParamsJson()); action.setFailurePolicy("CONTINUE");
        assertThrows(IllegalArgumentException.class, () -> publisher.validate("process"));
    }

    @Test void rejectsDifferentValuesAndClearVersusValueInParallelBranches() {
        when(actions.findDraftActions("process")).thenReturn(List.of(action("left", "flag", "true"), action("right", "flag", "false")));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> publisher.validate("process")).getMessage().contains("flag"));
        var clear = action("right", "flag", "false"); clear.setParamsJson(EntityFieldAssignmentFlowActionHandlerTest.clear("flag"));
        when(actions.findDraftActions("process")).thenReturn(List.of(action("left", "flag", "true"), clear));
        assertThrows(IllegalArgumentException.class, () -> publisher.validate("process"));
    }

    @Test void allowsSameValueDifferentFieldsAndWritesAfterJoin() {
        when(actions.findDraftActions("process")).thenReturn(List.of(action("left", "flag", "true"),
                action("right", "flag", "true"), action("after", "flag", "false")));
        assertDoesNotThrow(() -> publisher.validate("process"));
        when(actions.findDraftActions("process")).thenReturn(List.of(action("left", "flag", "true"), action("right", "other", "false")));
        assertDoesNotThrow(() -> publisher.validate("process"));
    }

    @Test void excludesDisabledActionsAndAllowsExclusiveAlternatives() {
        var disabled = action("right", "flag", "false"); disabled.setEnabled(false);
        when(actions.findDraftActions("process")).thenReturn(List.of(action("left", "flag", "true"), disabled));
        assertDoesNotThrow(() -> publisher.validate("process"));
        disabled.setEnabled(true); config.setBpmnXml(graph("exclusiveGateway"));
        assertDoesNotThrow(() -> publisher.validate("process"));
    }

    @Test void saveAndPublishPreserveStructuredLiteralParametersAndOldPublishedCopy() {
        var mapper = mock(com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper.class);
        var catalog = mock(com.workflow.contracts.process.action.port.FlowActionCatalogPort.class);
        when(catalog.requireSelectable(any(), any(), any())).thenReturn(
                new com.workflow.contracts.process.action.model.FlowActionDefinitionDescriptor("definition",
                        EntityFieldAssignmentFlowActionHandler.BEAN_NAME, "实体字段赋值"));
        var service = new FlowActionService(mapper, validator, catalog);
        var draft = action("left", "flag", "false");
        String original = draft.getParamsJson();
        service.saveAction(draft);
        when(mapper.findDraftActionsByProcessConfigId("process")).thenReturn(List.of(draft));
        service.publishActions("process", "release-1");
        var capture = org.mockito.ArgumentCaptor.forClass(FlowAction.class);
        verify(mapper, times(2)).insert(capture.capture());
        var published = capture.getAllValues().get(1);
        assertEquals("release-1", published.getVersionId()); assertEquals(original, published.getParamsJson());
        draft.setParamsJson(action("left", "flag", "true").getParamsJson());
        assertEquals(original, published.getParamsJson());
    }

    private FlowAction action(String flow, String field, String value) {
        var action = EntityFieldAssignmentFlowActionHandlerTest.action("{\"assignments\":[{\"fieldCode\":\"" + field + "\",\"operation\":\"SET\",\"value\":" + value + "}]}");
        action.setElementId(flow); action.setInterfaceName(EntityFieldAssignmentFlowActionHandler.BEAN_NAME); action.setEnabled(true);
        return action;
    }
    private String graph(String gateway) {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><process id=\"p\">"
                + "<" + gateway + " id=\"split\"/><userTask id=\"a\"/><userTask id=\"b\"/><parallelGateway id=\"join\"/><endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"left\" sourceRef=\"split\" targetRef=\"a\"/><sequenceFlow id=\"right\" sourceRef=\"split\" targetRef=\"b\"/>"
                + "<sequenceFlow id=\"aj\" sourceRef=\"a\" targetRef=\"join\"/><sequenceFlow id=\"bj\" sourceRef=\"b\" targetRef=\"join\"/>"
                + "<sequenceFlow id=\"after\" sourceRef=\"join\" targetRef=\"end\"/></process></definitions>";
    }
}
