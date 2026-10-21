package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.contracts.entity.port.EntityRecordQueryPort;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.process.assignment.application.InitiatorOrganizationSnapshotService;
import com.workflow.process.assignment.application.RelativeOrgPositionProcessInspector;
import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;
import com.workflow.process.definition.application.ProcessBpmnPublishSanitizer;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.engine.infrastructure.flowable.CalledProcessEntityListener;
import com.workflow.process.instance.application.CalledProcessRecordSource;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import com.workflow.process.status.application.ProcessEntityStatusPolicy;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.common.engine.impl.el.DefaultExpressionManager;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CalledProcessEntityRuntimeTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ProcessDefinitionConfigMapper configs = mock(ProcessDefinitionConfigMapper.class);
    private final EntityDefinitionMapper entities = mock(EntityDefinitionMapper.class);
    private final EntityRecordPort records = mock(EntityRecordPort.class);
    private final EntityRecordQueryPort queries = mock(EntityRecordQueryPort.class);
    private final EntityProcessLinkMapper links = mock(EntityProcessLinkMapper.class);
    private final ProcessEntityStatusPolicy statuses = mock(ProcessEntityStatusPolicy.class);
    private final RelativeOrgPositionProcessInspector positions = mock(RelativeOrgPositionProcessInspector.class);
    private final InitiatorOrganizationSnapshotService snapshots = mock(InitiatorOrganizationSnapshotService.class);
    private final MultiInstanceCollectionListener multiInstance = mock(MultiInstanceCollectionListener.class);

    @Test
    void childUsesItsOwnRecordAndParentContinuesOnlyAfterChildCompletes() throws Exception {
        try (var fixture = engine()) {
            ProcessEngine engine = fixture.engine;
            deploy(engine, "parent", parent(config()));
            var parent = engine.getRuntimeService().startProcessInstanceByKey("parent", Map.of(
                    "related", Map.of("id", "record-b"), "entityCode", "parent_entity", "entityDataId", "record-a",
                    "amount", 900, "parentOnly", "secret", "submitterId", "alice", "submitterName", "Alice"));
            var child = engine.getRuntimeService().createProcessInstanceQuery().superProcessInstanceId(parent.getId()).singleResult();
            assertNotNull(child);
            assertEquals("record-b", child.getBusinessKey());
            var variables = engine.getRuntimeService().getVariables(child.getId());
            assertEquals("child_entity", variables.get("entityCode"));
            assertEquals("record-b", variables.get("entityDataId"));
            assertEquals(120, variables.get("amount"));
            assertEquals(900, variables.get("parentAmount"));
            assertEquals("CHILD-001", variables.get("code"));
            assertEquals("alice", variables.get("initiator"));
            assertFalse(variables.containsKey("parentOnly"));
            assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(parent.getId()).count());
            var link = ArgumentCaptor.forClass(EntityProcessLink.class);
            verify(links).insert(link.capture());
            assertEquals(child.getId(), link.getValue().getProcessInstanceId());
            assertEquals("child_entity", link.getValue().getEntityCode());
            verify(records).bindCalledProcess(eq("child_entity"), eq("record-b"), anyString(), eq(child.getId()),
                    eq("alice"), eq("Alice"), isNull());
            verify(multiInstance).prepareVariables(eq(child.getProcessDefinitionId()), anyMap());
            var task = engine.getTaskService().createTaskQuery().processInstanceId(child.getId()).singleResult();
            engine.getTaskService().complete(task.getId(), Map.of("result", "approved"));
            assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(child.getId()).singleResult());
            assertEquals("after", engine.getTaskService().createTaskQuery().processInstanceId(parent.getId()).singleResult().getTaskDefinitionKey());
            assertEquals("approved", engine.getRuntimeService().getVariable(parent.getId(), "childResult"));
            assertEquals("record-a", engine.getRuntimeService().getVariable(parent.getId(), "entityDataId"));
            assertEquals(900, engine.getRuntimeService().getVariable(parent.getId(), "amount"));
        }
    }

    @Test
    void invalidRecordOrDisabledFlowRollsBackParentStart() throws Exception {
        try (var fixture = engine()) {
            deploy(fixture.engine, "parent", parent(config()));
            assertThrows(RuntimeException.class, () -> fixture.engine.getRuntimeService()
                    .startProcessInstanceByKey("parent", Map.of("related", Map.of(), "amount", 1)));
            verifyNoInteractions(records);
            assertEquals(0, fixture.engine.getRuntimeService().createProcessInstanceQuery().count());
            when(configs.findByProcessKey("child")).thenReturn(Optional.empty());
            assertThrows(RuntimeException.class, () -> fixture.engine.getRuntimeService()
                    .startProcessInstanceByKey("parent", Map.of("related", Map.of("id", "record-b"), "amount", 1)));
            assertEquals(0, fixture.engine.getRuntimeService().createProcessInstanceQuery().count());
        }
    }

    @Test
    void occupiedRecordFailsWithoutCreatingChildTask() throws Exception {
        try (var fixture = engine()) {
            doThrow(new IllegalArgumentException("已发起流程")).when(records)
                    .bindCalledProcess(anyString(), anyString(), anyString(), anyString(), any(), any(), any());
            deploy(fixture.engine, "parent", parent(config()));
            assertThrows(RuntimeException.class, () -> fixture.engine.getRuntimeService().startProcessInstanceByKey("parent",
                    Map.of("related", Map.of("id", "record-b"), "amount", 1)));
            assertEquals(0, fixture.engine.getTaskService().createTaskQuery().count());
            verifyNoInteractions(links);
        }
    }

    @Test
    void legacyCallDoesNotBindAnEntityRecord() throws Exception {
        try (var fixture = engine()) {
            var config = config();
            config.remove("entityBinding");
            deploy(fixture.engine, "parent", parent(config));
            fixture.engine.getRuntimeService().startProcessInstanceByKey("parent", Map.of("amount", 7));
            assertEquals(1, fixture.engine.getTaskService().createTaskQuery().count());
            verifyNoInteractions(records, links, queries);
        }
    }

    @Test
    void publishingRejectsSelfCallsUnsafeSourcesAndReservedMappingTargets() throws Exception {
        for (var override : List.of(Map.of("calledElement", "parent"), Map.of("entityRecordId", "${bean.run()}"),
                Map.of("entityRecordId", ""), Map.of("inputParameters", "{\"entityCode\":\"fake\"}"),
                Map.of("outputParameters", "{\"entityDataId\":\"childId\"}"))) {
            var config = config(); config.putAll(override);
            assertThrows(IllegalArgumentException.class, () -> parent(config));
        }
        assertEquals("123", CalledProcessRecordSource.resolve("${relation.id}", key -> Map.of("id", 123)));
        assertThrows(IllegalArgumentException.class, () -> CalledProcessRecordSource.resolve("ids", key -> List.of("a", "b")));
    }

    private Map<String, String> config() {
        return new java.util.LinkedHashMap<>(Map.of("calledElement", "child", "callActivityType", "bpmn",
                "entityBinding", "existing", "entityRecordId", "${related.id}",
                "inputParameters", "{\"parentAmount\":\"${amount}\"}",
                "outputParameters", "{\"childResult\":\"${result}\"}"));
    }

    private String parent(Map<String, String> config) throws Exception {
        String property = json.writeValueAsString(config).replace("&", "&amp;").replace("\"", "&quot;");
        String xml = definitions("""
                <process id="parent" isExecutable="true">
                  <startEvent id="start"/><callActivity id="call" flowable:inheritVariables="true">
                    <extensionElements><flowable:properties><flowable:property name="callConfig" value="%s"/></flowable:properties></extensionElements>
                  </callActivity><userTask id="after"/><endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="call"/>
                  <sequenceFlow id="f2" sourceRef="call" targetRef="after"/>
                  <sequenceFlow id="f3" sourceRef="after" targetRef="end"/>
                </process>
                """.formatted(property));
        return new ProcessBpmnPublishSanitizer(json).sanitize(xml, "parent");
    }

    private Fixture engine() {
        var configuration = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:called_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        configuration.setExpressionManager(new DefaultExpressionManager(Map.of()));
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        ProcessEngine engine = configuration.buildProcessEngine();
        engine.getRuntimeService().addEventListener(new CalledProcessEntityListener(json, configs, entities, records,
                queries, links, statuses, positions, snapshots, multiInstance, engine.getRuntimeService()));
        var config = new ProcessDefinitionConfig(); config.setId("2"); config.setProcessKey("child");
        config.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        when(configs.findByProcessKey("child")).thenReturn(Optional.of(config));
        var entity = new EntityDefinition(); entity.setEntityCode("child_entity");
        when(entities.findAllByProcessDefinitionId("2")).thenReturn(List.of(entity));
        when(statuses.usesTransitions(anyString())).thenReturn(true);
        var record = new EntityRecordData(); record.setId("record-b"); record.setCode("CHILD-001");
        record.setData(Map.of("amount", 120, "entityCode", "forged", "initiator", "forged"));
        when(queries.findById("child_entity", "record-b")).thenReturn(record);
        deploy(engine, "child", definitions("""
                <process id="child" isExecutable="true"><startEvent id="s"/><userTask id="review"/><endEvent id="e"/>
                <sequenceFlow id="c1" sourceRef="s" targetRef="review"/><sequenceFlow id="c2" sourceRef="review" targetRef="e"/></process>
                """));
        return new Fixture(engine);
    }
    private static String definitions(String body) {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"test\">" + body + "</definitions>";
    }
    private void deploy(ProcessEngine engine, String name, String xml) {
        engine.getRepositoryService().createDeployment().addString(name + ".bpmn20.xml", xml).deploy();
    }
    private record Fixture(ProcessEngine engine) implements AutoCloseable {
        public void close() { engine.close(); }
    }
}
