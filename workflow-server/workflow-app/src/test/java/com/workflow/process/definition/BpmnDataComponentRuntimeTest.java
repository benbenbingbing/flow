package com.workflow.process.definition;

import com.workflow.process.definition.application.ProcessBpmnPublishSanitizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.DataAssociation;
import org.flowable.bpmn.model.DataStoreReference;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.DataObject;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flowable 运行时对 BPMN 数据组件的语义验证。 */
class BpmnDataComponentRuntimeTest {

    /**
     * bpmn-js 把可见名称放在 dataObjectReference 上；发布清洗后该名称应成为真实流程变量名。
     */
    @Test
    void dataObjectReferenceNameBecomesRuntimeDataObjectVariable() {
        ProcessEngine engine = buildEngine();
        try {
            String runtimeXml = new ProcessBpmnPublishSanitizer(new ObjectMapper())
                    .sanitize(dataObjectBpmn(), "data_object_runtime");
            deploy(engine, "data-object.bpmn20.xml", runtimeXml);

            RuntimeService runtimeService = engine.getRuntimeService();
            ProcessInstance instance = runtimeService
                    .startProcessInstanceByKey("data_object_runtime");

            assertTrue(runtimeService.hasVariable(
                    instance.getId(), "组件测试数据对象"));
            assertNull(runtimeService.getVariable(
                    instance.getId(), "组件测试数据对象"));
            DataObject dataObject = runtimeService.getDataObject(
                    instance.getId(), "组件测试数据对象");
            assertNotNull(dataObject);
            assertEquals("DataObject_1", dataObject.getDataObjectDefinitionKey());
            assertEquals("组件测试数据对象", dataObject.getName());

            ProcessInstance overridden = runtimeService.startProcessInstanceByKey(
                    "data_object_runtime",
                    Map.of("组件测试数据对象", "表单传入值"));
            assertEquals(
                    "表单传入值",
                    runtimeService.getVariable(
                            overridden.getId(), "组件测试数据对象"));
        } finally {
            engine.close();
        }
    }

    /**
     * DataStore/Reference 可以进入部署模型，但 Flowable 不会把它创建为变量或跨实例共享存储。
     */
    @Test
    void dataStoreReferenceIsModelMetadataNotSharedRuntimeStorage() {
        ProcessEngine engine = buildEngine();
        try {
            ProcessDefinition definition = deploy(
                    engine,
                    "data-store.bpmn20.xml",
                    dataStoreBpmn());
            BpmnModel model = engine.getRepositoryService()
                    .getBpmnModel(definition.getId());

            assertEquals(
                    "共享业务数据",
                    model.getDataStore("DataStore_1").getName());
            DataStoreReference reference = (DataStoreReference) model
                    .getFlowElement("DataStoreReference_1");
            assertNotNull(reference);
            assertEquals("共享业务数据存储", reference.getName());
            // Flowable 7.2 仅保留该图元，转换器不会解析 dataStoreRef，也没有对应的运行时存储。
            assertNull(reference.getDataStoreRef());

            RuntimeService runtimeService = engine.getRuntimeService();
            ProcessInstance first = runtimeService
                    .startProcessInstanceByKey("data_store_runtime");
            runtimeService.setVariable(first.getId(), "sharedValue", "first");
            ProcessInstance second = runtimeService
                    .startProcessInstanceByKey("data_store_runtime");

            assertFalse(runtimeService.hasVariable(
                    first.getId(), "DataStoreReference_1"));
            assertFalse(runtimeService.hasVariable(
                    second.getId(), "sharedValue"));
        } finally {
            engine.close();
        }
    }

    /**
     * 普通 JavaDelegate 服务任务会保留关联模型，但不会自动执行输入/输出变量搬运。
     */
    @Test
    void genericActivityAssociationsAreParsedButDoNotTransferVariables() {
        ProcessEngine engine = buildEngine();
        try {
            ProcessDefinition definition = deploy(
                    engine,
                    "data-association.bpmn20.xml",
                    dataAssociationBpmn());
            BpmnModel model = engine.getRepositoryService()
                    .getBpmnModel(definition.getId());
            Activity serviceTask = (Activity) model
                    .getFlowElement("association-probe");

            DataAssociation input = serviceTask
                    .getDataInputAssociations().get(0);
            assertEquals("DataObjectReference_Input", input.getSourceRef());
            assertEquals("DataInput_1", input.getTargetRef());
            DataAssociation output = serviceTask
                    .getDataOutputAssociations().get(0);
            assertEquals("DataOutput_1", output.getSourceRef());
            assertEquals("DataObjectReference_Output", output.getTargetRef());

            RuntimeService runtimeService = engine.getRuntimeService();
            ProcessInstance instance = runtimeService
                    .startProcessInstanceByKey("data_association_runtime");

            assertEquals(
                    "输入初始值",
                    runtimeService.getVariable(instance.getId(), "输入数据"));
            assertEquals(
                    false,
                    runtimeService.getVariable(
                            instance.getId(), "inputAssociationApplied"));
            assertEquals(
                    "delegate-output",
                    runtimeService.getVariable(instance.getId(), "DataOutput_1"));
            assertNull(runtimeService.getVariable(instance.getId(), "输出数据"));
            assertFalse(runtimeService.hasVariable(
                    instance.getId(), "DataObjectReference_Output"));
        } finally {
            engine.close();
        }
    }

    /**
     * 池、分组、展开子流程与数据组件组合使用时，应保持责任边界、控制流和真实变量语义。
     */
    @Test
    void paletteComponentsWorkTogetherInTypicalApplicationReviewFlow() {
        ProcessEngine engine = buildEngine();
        try {
            String runtimeXml = new ProcessBpmnPublishSanitizer(new ObjectMapper())
                    .sanitize(componentScenarioBpmn(), "component_scenario_runtime");
            ProcessDefinition definition = deploy(
                    engine,
                    "component-scenario.bpmn20.xml",
                    runtimeXml);
            BpmnModel model = engine.getRepositoryService()
                    .getBpmnModel(definition.getId());

            assertEquals(1, model.getPools().size());
            assertEquals(
                    "component_scenario_runtime",
                    model.getPools().get(0).getProcessRef());
            assertTrue(runtimeXml.contains(
                    "bpmnElement=\"Collaboration_Component\""));
            assertTrue(runtimeXml.contains(
                    "categoryValueRef=\"CategoryValue_Review\""));

            SubProcess reviewSubProcess = (SubProcess) model
                    .getFlowElement("ReviewSubProcess");
            assertNotNull(reviewSubProcess);
            assertNotNull(reviewSubProcess.getFlowElement("ReviewMaterialsTask"));
            assertEquals(
                    Boolean.TRUE,
                    model.getGraphicInfo("ReviewSubProcess").getExpanded());
            // Flowable 7.2 不执行子流程边界数据关联，发布资源仍必须完整保留这两条建模关系。
            assertTrue(runtimeXml.contains(
                    "<bpmn:sourceRef>DataObjectReference_Application</bpmn:sourceRef>"));
            assertTrue(runtimeXml.contains(
                    "<bpmn:targetRef>DataStoreReference_Archive</bpmn:targetRef>"));
            assertNotNull(model.getDataStore("DataStore_Archive"));
            assertNotNull(model.getFlowElement("DataStoreReference_Archive"));

            RuntimeService runtimeService = engine.getRuntimeService();
            TaskService taskService = engine.getTaskService();
            ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                    "component_scenario_runtime",
                    Map.of("申请材料", "合同与报价附件"));

            Task internalTask = taskService.createTaskQuery()
                    .processInstanceId(instance.getId())
                    .singleResult();
            assertNotNull(internalTask);
            assertEquals("ReviewMaterialsTask", internalTask.getTaskDefinitionKey());
            assertEquals(
                    "合同与报价附件",
                    taskService.getVariable(internalTask.getId(), "申请材料"));

            // Group 与 DataStoreReference 是建模图元，不应创建变量或改变子流程的任务推进。
            assertFalse(runtimeService.hasVariable(
                    instance.getId(), "Group_Review"));
            assertFalse(runtimeService.hasVariable(
                    instance.getId(), "DataStoreReference_Archive"));
            assertFalse(runtimeService.hasVariable(
                    instance.getId(), "DataInput_SubProcess"));
            taskService.complete(internalTask.getId());

            Task followUpTask = taskService.createTaskQuery()
                    .processInstanceId(instance.getId())
                    .singleResult();
            assertNotNull(followUpTask);
            assertEquals("ArchiveFollowUpTask", followUpTask.getTaskDefinitionKey());
            assertEquals(
                    "合同与报价附件",
                    runtimeService.getVariable(instance.getId(), "申请材料"));
            assertFalse(runtimeService.hasVariable(
                    instance.getId(), "DataOutput_Archive"));
            taskService.complete(followUpTask.getId());

            assertNull(runtimeService.createProcessInstanceQuery()
                    .processInstanceId(instance.getId())
                    .singleResult());
        } finally {
            engine.close();
        }
    }

    /** 记录普通服务任务执行时输入关联是否已经生成目标变量。 */
    public static class AssociationProbeDelegate implements JavaDelegate {

        @Override
        public void execute(DelegateExecution execution) {
            execution.setVariable(
                    "inputAssociationApplied",
                    execution.hasVariable("DataInput_1"));
            execution.setVariable("DataOutput_1", "delegate-output");
        }
    }

    private ProcessEngine buildEngine() {
        ProcessEngineConfigurationImpl configuration =
                (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
                        .createStandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl(
                "jdbc:h2:mem:bpmn_data_" + UUID.randomUUID()
                        + ";DB_CLOSE_DELAY=-1");
        configuration.setDatabaseSchemaUpdate(
                ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setCreateDiagramOnDeploy(false);
        return configuration.buildProcessEngine();
    }

    private ProcessDefinition deploy(
            ProcessEngine engine,
            String resourceName,
            String bpmnXml) {
        RepositoryService repositoryService = engine.getRepositoryService();
        repositoryService.createDeployment()
                .addString(resourceName, bpmnXml)
                .deploy();
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionResourceName(resourceName)
                .singleResult();
    }

    private String dataObjectBpmn() {
        return definitions("""
                <bpmn:process id="draft_data_object" isExecutable="true">
                  <bpmn:dataObject id="DataObject_1" />
                  <bpmn:dataObjectReference id="DataObjectReference_1"
                      name="组件测试数据对象" dataObjectRef="DataObject_1" />
                  <bpmn:startEvent id="start" />
                  <bpmn:userTask id="wait" />
                  <bpmn:endEvent id="end" />
                  <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="wait" />
                  <bpmn:sequenceFlow id="flow-2" sourceRef="wait" targetRef="end" />
                </bpmn:process>
                """);
    }

    private String dataStoreBpmn() {
        return definitions("""
                <bpmn:dataStore id="DataStore_1" name="共享业务数据" />
                <bpmn:process id="data_store_runtime" isExecutable="true">
                  <bpmn:dataStoreReference id="DataStoreReference_1"
                      name="共享业务数据存储" dataStoreRef="DataStore_1" />
                  <bpmn:startEvent id="start" />
                  <bpmn:userTask id="wait" />
                  <bpmn:endEvent id="end" />
                  <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="wait" />
                  <bpmn:sequenceFlow id="flow-2" sourceRef="wait" targetRef="end" />
                </bpmn:process>
                """);
    }

    private String dataAssociationBpmn() {
        return definitions("""
                <bpmn:process id="data_association_runtime" isExecutable="true">
                  <bpmn:dataObject id="DataObject_Input" name="输入数据">
                    <bpmn:extensionElements>
                      <flowable:value>输入初始值</flowable:value>
                    </bpmn:extensionElements>
                  </bpmn:dataObject>
                  <bpmn:dataObjectReference id="DataObjectReference_Input"
                      name="输入数据" dataObjectRef="DataObject_Input" />
                  <bpmn:dataObject id="DataObject_Output" name="输出数据" />
                  <bpmn:dataObjectReference id="DataObjectReference_Output"
                      name="输出数据" dataObjectRef="DataObject_Output" />
                  <bpmn:startEvent id="start" />
                  <bpmn:serviceTask id="association-probe"
                      flowable:class="%s">
                    <bpmn:ioSpecification>
                      <bpmn:dataInput id="DataInput_1" name="delegateInput" />
                      <bpmn:dataOutput id="DataOutput_1" name="delegateOutput" />
                      <bpmn:inputSet id="InputSet_1">
                        <bpmn:dataInputRefs>DataInput_1</bpmn:dataInputRefs>
                      </bpmn:inputSet>
                      <bpmn:outputSet id="OutputSet_1">
                        <bpmn:dataOutputRefs>DataOutput_1</bpmn:dataOutputRefs>
                      </bpmn:outputSet>
                    </bpmn:ioSpecification>
                    <bpmn:dataInputAssociation id="DataInputAssociation_1">
                      <bpmn:sourceRef>DataObjectReference_Input</bpmn:sourceRef>
                      <bpmn:targetRef>DataInput_1</bpmn:targetRef>
                    </bpmn:dataInputAssociation>
                    <bpmn:dataOutputAssociation id="DataOutputAssociation_1">
                      <bpmn:sourceRef>DataOutput_1</bpmn:sourceRef>
                      <bpmn:targetRef>DataObjectReference_Output</bpmn:targetRef>
                    </bpmn:dataOutputAssociation>
                  </bpmn:serviceTask>
                  <bpmn:userTask id="wait" />
                  <bpmn:endEvent id="end" />
                  <bpmn:sequenceFlow id="flow-1" sourceRef="start"
                      targetRef="association-probe" />
                  <bpmn:sequenceFlow id="flow-2" sourceRef="association-probe"
                      targetRef="wait" />
                  <bpmn:sequenceFlow id="flow-3" sourceRef="wait" targetRef="end" />
                </bpmn:process>
                """.formatted(AssociationProbeDelegate.class.getName()));
    }

    /**
     * 贴近设计器典型用法的聚合 BPMN：单池标识系统边界，Group 标注审核阶段，展开子流程
     * 承载人工审核，数据对象作为申请材料变量，归档库与输入输出关联保留模型关系。
     */
    private String componentScenarioBpmn() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions
                    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                    xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                    xmlns:flowable="http://flowable.org/bpmn"
                    targetNamespace="http://workflow.test/process">
                  <bpmn:category id="Category_Review">
                    <bpmn:categoryValue id="CategoryValue_Review" value="材料审核阶段" />
                  </bpmn:category>
                  <bpmn:dataStore id="DataStore_Archive" name="归档库" />
                  <bpmn:collaboration id="Collaboration_Component">
                    <bpmn:participant id="Participant_ApplicationSystem"
                        name="申请受理系统" processRef="Draft_Component_Process" />
                  </bpmn:collaboration>
                  <bpmn:process id="Draft_Component_Process" isExecutable="true">
                    <bpmn:dataObject id="DataObject_Application" />
                    <bpmn:dataObjectReference id="DataObjectReference_Application"
                        name="申请材料" dataObjectRef="DataObject_Application" />
                    <bpmn:dataStoreReference id="DataStoreReference_Archive"
                        name="归档库" dataStoreRef="DataStore_Archive" />
                    <bpmn:startEvent id="ApplicationStart" />
                    <bpmn:subProcess id="ReviewSubProcess" name="材料审核">
                      <bpmn:ioSpecification>
                        <bpmn:dataInput id="DataInput_SubProcess" name="待审材料" />
                        <bpmn:dataOutput id="DataOutput_Archive" name="归档结果" />
                        <bpmn:inputSet id="InputSet_SubProcess">
                          <bpmn:dataInputRefs>DataInput_SubProcess</bpmn:dataInputRefs>
                        </bpmn:inputSet>
                        <bpmn:outputSet id="OutputSet_SubProcess">
                          <bpmn:dataOutputRefs>DataOutput_Archive</bpmn:dataOutputRefs>
                        </bpmn:outputSet>
                      </bpmn:ioSpecification>
                      <bpmn:dataInputAssociation id="Association_Application_Input">
                        <bpmn:sourceRef>DataObjectReference_Application</bpmn:sourceRef>
                        <bpmn:targetRef>DataInput_SubProcess</bpmn:targetRef>
                      </bpmn:dataInputAssociation>
                      <bpmn:dataOutputAssociation id="Association_Archive_Output">
                        <bpmn:sourceRef>DataOutput_Archive</bpmn:sourceRef>
                        <bpmn:targetRef>DataStoreReference_Archive</bpmn:targetRef>
                      </bpmn:dataOutputAssociation>
                      <bpmn:startEvent id="ReviewStart" />
                      <bpmn:userTask id="ReviewMaterialsTask" name="审核申请材料" />
                      <bpmn:endEvent id="ReviewEnd" />
                      <bpmn:sequenceFlow id="ReviewFlow_1"
                          sourceRef="ReviewStart" targetRef="ReviewMaterialsTask" />
                      <bpmn:sequenceFlow id="ReviewFlow_2"
                          sourceRef="ReviewMaterialsTask" targetRef="ReviewEnd" />
                    </bpmn:subProcess>
                    <bpmn:userTask id="ArchiveFollowUpTask" name="确认归档" />
                    <bpmn:endEvent id="ApplicationEnd" />
                    <bpmn:sequenceFlow id="ApplicationFlow_1"
                        sourceRef="ApplicationStart" targetRef="ReviewSubProcess" />
                    <bpmn:sequenceFlow id="ApplicationFlow_2"
                        sourceRef="ReviewSubProcess" targetRef="ArchiveFollowUpTask" />
                    <bpmn:sequenceFlow id="ApplicationFlow_3"
                        sourceRef="ArchiveFollowUpTask" targetRef="ApplicationEnd" />
                    <bpmn:group id="Group_Review"
                        categoryValueRef="CategoryValue_Review" />
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="Diagram_Component">
                    <bpmndi:BPMNPlane id="Plane_Component"
                        bpmnElement="Collaboration_Component">
                      <bpmndi:BPMNShape id="ReviewSubProcess_di"
                          bpmnElement="ReviewSubProcess" isExpanded="true">
                        <dc:Bounds x="180" y="120" width="420" height="220" />
                      </bpmndi:BPMNShape>
                    </bpmndi:BPMNPlane>
                  </bpmndi:BPMNDiagram>
                </bpmn:definitions>
                """;
    }

    private String definitions(String elements) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions
                    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn"
                    targetNamespace="http://workflow.test/process">
                  %s
                </bpmn:definitions>
                """.formatted(elements);
    }
}
