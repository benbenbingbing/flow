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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 流程 BPMN 表单引用迁移往返兼容测试。 */
class ConfigMigrationBpmnFormReferenceRoundTripTest {

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

        assertTrue(portableBpmn.contains(bindingModeProperty));
        assertTrue(portableBpmn.contains(
                "<flowable:property name=\"entityFormId\" value=\""
                        + portableFormRef + "\" />"));

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

        assertTrue(importedBpmn.contains(bindingModeProperty));
        assertTrue(importedBpmn.contains(
                "<flowable:property name=\"entityFormId\" value=\""
                        + targetFormId + "\" />"));
        assertFalse(importedBpmn.contains(portableFormRef));
        assertFalse(importedBpmn.contains(sourceFormId));
    }
}
