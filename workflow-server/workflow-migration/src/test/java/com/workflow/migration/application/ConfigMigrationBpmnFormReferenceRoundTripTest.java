package com.workflow.migration.application;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigEnvironmentMappingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 流程 BPMN 表单引用迁移往返兼容测试。 */
class ConfigMigrationBpmnFormReferenceRoundTripTest {

    @Test
    void rewritesOnlyDeclaredBindingsIncludingListsAndAlternatePrefixes() {
        String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:c="http://camunda.org/schema/1.0/bpmn">
                  <process id="expense">
                    <userTask id="form-1" name="form-1" c:formKey="form-1">
                      <documentation>表单 form-1，子串 form-10，字面量 wf-form://expense/main</documentation>
                      <extensionElements><c:properties>
                        <c:property name="entityFormIds" value="[&quot;form-1&quot;,&quot;form-10&quot;]"/>
                        <c:property name="entityFormBindingMode" value="form-1"/>
                      </c:properties><c:entityFormId>form-1</c:entityFormId></extensionElements>
                    </userTask>
                    <userTask id="other" formKey="form-1,form-10"/>
                    <sequenceFlow id="edge" sourceRef="form-1" targetRef="other"/>
                    <scriptTask id="script"><script>use('form-1');</script></scriptTask>
                  </process>
                </definitions>
                """;
        Map<String, String> bindings = Map.of("form-1", "wf-form://expense/main");
        String portable = ConfigMigrationBpmnReferences.rewriteForms(xml, value -> bindings.getOrDefault(value, value));
        String target = ConfigMigrationBpmnReferences.rewriteForms(portable,
                value -> value.equals("wf-form://expense/main") ? "target-form" : value);
        assertTrue(target.contains("c:formKey=\"target-form\""));
        assertTrue(target.contains("formKey=\"target-form,form-10\""));
        assertTrue(target.contains("[&quot;target-form&quot;,&quot;form-10&quot;]"));
        assertTrue(target.contains("<c:entityFormId>target-form</c:entityFormId>"));
        assertTrue(target.contains("id=\"form-1\""));
        assertTrue(target.contains("sourceRef=\"form-1\""));
        assertTrue(target.contains("name=\"entityFormBindingMode\" value=\"form-1\""));
        assertTrue(target.contains("表单 form-1，子串 form-10，字面量 wf-form://expense/main"));
        assertTrue(target.contains("use('form-1');"));
        assertEquals(xml, ConfigMigrationBpmnReferences.rewriteForms(xml, value -> value));
    }

    @Test
    void rejectsExternalEntityDocuments() {
        String xml = "<!DOCTYPE definitions [<!ENTITY xxe SYSTEM 'file:///not-readable'>]>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">&xxe;</definitions>";
        assertThrows(IllegalArgumentException.class, () -> ConfigMigrationBpmnReferences.rewriteForms(xml, value -> value));
    }

    @Test
    void sourceResolvesAdditionalBindingsMissingFromThePrimaryNodeFormTable() {
        var assets = mock(ConfigMigrationAssetService.class, CALLS_REAL_METHODS);
        var forms = mock(EntityFormMapper.class);
        var entities = mock(EntityDefinitionMapper.class);
        var form = new EntityForm(); form.setId("extra-form-id"); form.setEntityId("entity-id"); form.setFormKey("extra");
        var entity = new EntityDefinition(); entity.setId("entity-id"); entity.setEntityCode("expense");
        when(forms.selectById("extra-form-id")).thenReturn(form);
        when(entities.selectById("entity-id")).thenReturn(entity);
        ReflectionTestUtils.setField(assets, "formMapper", forms);
        ReflectionTestUtils.setField(assets, "entityMapper", entities);
        String xml = ConfigMigrationAssignmentSupportTest.bpmn("<bpmn:userTask id=\"review\"><bpmn:extensionElements>"
                + "<flowable:properties><flowable:property name=\"entityFormIds\" value=\"primary-id,extra-form-id\"/>"
                + "</flowable:properties></bpmn:extensionElements></bpmn:userTask>");
        String portable = ReflectionTestUtils.invokeMethod(assets, "replacePortableForms", xml,
                Map.of("primary-id", "wf-form://expense/main"));
        assertEquals(java.util.Set.of("wf-form://expense/main", "wf-form://expense/extra"),
                ConfigMigrationBpmnReferences.formReferences(portable));
        var error = assertThrows(IllegalArgumentException.class, () -> ConfigMigrationBpmnReferences.formReferences(xml));
        assertTrue(error.getMessage().contains("重新选择实体表单"));
    }

    @Test
    void collectsAllBpmnFormDependenciesAndRejectsRawBindingsDuringAnalysis() {
        var references = new ConfigMigrationReferenceService(null, null, null, null, null, null);
        String xml = ConfigMigrationAssignmentSupportTest.bpmn("<bpmn:userTask id=\"review\" flowable:formKey=\"wf-form://expense/main,wf-form://expense/extra\"/>");
        Map<String, Object> portable = references.exportReferences(Map.of("bpmnXml", xml));
        assertEquals(2, ConfigMigrationReferenceService.maps(portable.get("dependencies")).stream()
                .filter(value -> "FORM".equals(value.get("type"))).count());
        String raw = xml.replace("wf-form://expense/extra", "source-form-id");
        var error = assertThrows(IllegalArgumentException.class,
                () -> references.importReferences(Map.of("bpmnXml", raw), (type, key) -> key));
        assertTrue(error.getMessage().contains("重新选择实体表单"));
    }

    /**
     * 表单 ID 转为可移植引用再解析为目标 ID 时，未知的设计期绑定模式属性必须原样保留。
     */
    @Test
    void preservesUnknownBindingModePropertyAcrossFormReferenceRoundTrip() {
        String sourceFormId = "source-form-id";
        String targetFormId = "target-form-id";
        String portableFormRef = "wf-form://expense/approval";
        String bindingModeProperty =
                "<flowable:property name=\"entityFormBindingMode\" value=\"DEFAULT\" />";
        String sourceBpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn">
                  <bpmn:process id="expense_process">
                    <bpmn:userTask id="review">
                      <bpmn:extensionElements>
                        <flowable:properties>
                          %s
                          <flowable:property name="entityFormId" value="%s" />
                        </flowable:properties>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(bindingModeProperty, sourceFormId);

        // 直接调用生产导出替换逻辑，锁定未知 flowable:property 不被重写或删除。
        ConfigMigrationAssetService assetService = mock(
                ConfigMigrationAssetService.class,
                CALLS_REAL_METHODS);
        String portableBpmn = ReflectionTestUtils.invokeMethod(
                assetService,
                "replacePortableForms",
                sourceBpmn,
                Map.of(sourceFormId, portableFormRef));

        assertTrue(portableBpmn.contains("name=\"entityFormBindingMode\" value=\"DEFAULT\""));
        assertTrue(portableBpmn.contains(
                "<flowable:property name=\"entityFormId\" value=\""
                        + portableFormRef + "\""));

        ConfigEnvironmentMappingMapper mappingMapper =
                mock(ConfigEnvironmentMappingMapper.class);
        EntityDefinitionMapper entityMapper =
                mock(EntityDefinitionMapper.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityDefinition entity = new EntityDefinition();
        entity.setId("target-entity-id");
        EntityForm form = new EntityForm();
        form.setId(targetFormId);
        when(entityMapper.findByEntityCode("expense"))
                .thenReturn(Optional.of(entity));
        when(formMapper.selectByEntityIdAndFormKey(
                "target-entity-id",
                "approval"))
                .thenReturn(form);

        // 导入解析只替换 formRef；环境映射未配置时沿用原实体编码和表单键。
        ConfigMigrationImportApplyService importService = mock(
                ConfigMigrationImportApplyService.class,
                CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(
                importService,
                "environmentMappingMapper",
                mappingMapper);
        ReflectionTestUtils.setField(importService, "entityMapper", entityMapper);
        ReflectionTestUtils.setField(importService, "formMapper", formMapper);
        String importedBpmn = ReflectionTestUtils.invokeMethod(
                importService,
                "resolvePortableBpmn",
                portableBpmn,
                Map.of("nodeForms", List.of(Map.of(
                        "formRef",
                        portableFormRef))));

        assertTrue(importedBpmn.contains("name=\"entityFormBindingMode\" value=\"DEFAULT\""));
        assertTrue(importedBpmn.contains(
                "<flowable:property name=\"entityFormId\" value=\""
                        + targetFormId + "\""));
        assertFalse(importedBpmn.contains(portableFormRef));
        assertFalse(importedBpmn.contains(sourceFormId));
    }
}
