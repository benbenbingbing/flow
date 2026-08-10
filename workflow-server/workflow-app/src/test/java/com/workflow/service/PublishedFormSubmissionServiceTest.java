package com.workflow.service;

import com.workflow.entity.form.application.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiDataSourceDefinitionValidator;
import com.workflow.entity.ui.application.UiDataSourceService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 已发布表单提交服务测试。
 *
 * <p>被测对象：{@link PublishedFormSubmissionService}，覆盖节点级 beforeSubmit 执行与响应合并、
 * 发布节点缺失时执行字段级绑定、同一业务提交复用绑定幂等键、数据源失败保持 fail-closed、
 * 钉版发布精确执行、表单级 beforeSubmit 绑定等场景。
 */
class PublishedFormSubmissionServiceTest {

    /** 测试节点 beforeSubmit 恰好执行一次并合并响应：验证操作、目标、输入映射、输出映射与幂等键符合预期 */
    @Test
    void executesNodeBeforeSubmitOnceAndMergesResponse() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        PublishedFormSubmissionService service =
                service(releaseService, dataSourceService);

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        EntityFormField derivedField = new EntityFormField();
        derivedField.setDataSourceBindings(Map.of(
                "BEFORE_SUBMIT",
                Map.of(
                        "serviceId", "source-1",
                        "operationCode", "beforeSubmit")));
        EntityFormNode node = new EntityFormNode();
        node.setId("node-1");
        node.setNodeKey("amount-node");
        node.setDataSourceBindingsDocument(
                """
                {"BEFORE_SUBMIT":{
                  "serviceId":"source-1",
                  "operationCode":"beforeSubmit",
                  "inputMapping":{
                    "payload.amount":"data.amount",
                    "payload.mode":"context.mode"
                  },
                  "outputMapping":{
                    "normalized":"data.result.valid"
                  }
                }}
                """);
        form.setFields(List.of(derivedField));
        form.setNodes(List.of(node));
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));
        when(dataSourceService.execute(
                eq("source-1"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of(
                        "result", Map.of("valid", true)));

        Map<String, Object> result = service.applyForm(
                "form-1",
                "expense",
                "record-1",
                "edit",
                Map.of("amount", 88),
                executionContext("trace-1"));

        assertEquals(
                Map.of("amount", 88, "normalized", true),
                result);
        ArgumentCaptor<UiDataSourceExecuteRequest> captor =
                ArgumentCaptor.forClass(
                        UiDataSourceExecuteRequest.class);
        verify(dataSourceService, times(1))
                .execute(eq("source-1"), captor.capture());
        assertEquals("BEFORE_SUBMIT", captor.getValue().getUsage());
        assertEquals(
                "beforeSubmit",
                captor.getValue().getOperationCode());
        assertEquals("NODE", captor.getValue().getTargetType());
        assertEquals(
                "amount-node",
                captor.getValue().getTargetKey());
        assertEquals("expense", captor.getValue().getEntityCode());
        assertEquals(
                "release-1",
                captor.getValue().getReleaseId());
        assertEquals(
                1,
                captor.getValue().getReleaseVersion());
        assertFalse(captor.getValue().isServerPinnedRelease());
        assertEquals(
                Map.of(
                        "amount", 88,
                        "mode", "edit"),
                captor.getValue().getInput().get("payload"));
        assertEquals(
                "trace-1",
                captor.getValue().getInput().get(
                        "businessTraceKey"));
        assertTrue(String.valueOf(
                captor.getValue().getInput().get(
                        "idempotencyKey")).startsWith("fbs_"));
        assertEquals(
                captor.getValue().getInput().get(
                        "idempotencyKey"),
                captor.getValue().getServerIdempotencyKey());
    }

    /** 测试发布节点缺失时执行字段级绑定：验证按字段级接口操作执行并合并结果 */
    @Test
    void executesFieldBindingsWhenPublishedNodesAreAbsent() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        PublishedFormSubmissionService service =
                service(releaseService, dataSourceService);

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        EntityFormField field = new EntityFormField();
        field.setDataSourceBindings(Map.of(
                "BEFORE_SUBMIT",
                Map.of(
                        "serviceId", "source-1",
                        "operationCode", "beforeSubmit")));
        form.setFields(List.of(field));
        form.setNodes(List.of());
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));
        when(dataSourceService.execute(
                eq("source-1"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("approved", true));

        Map<String, Object> result = service.applyForm(
                "form-1",
                "expense",
                null,
                "create",
                Map.of("amount", 88));

        assertEquals(
                Map.of("amount", 88, "approved", true),
                result);
        verify(dataSourceService, times(1))
                .execute(
                        eq("source-1"),
                        org.mockito.ArgumentMatchers.any());
    }

    /** 当前发布表单未声明旧关系字段时，提交数据必须忽略该字段，避免空数组误删独立子列表数据 */
    @Test
    void removesRelationDataNotDeclaredByPublishedForm() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        PublishedFormSubmissionService service =
                service(
                        mock(EntityDefinitionMapper.class),
                        relationMapper,
                        releaseService,
                        mock(UiDataSourceService.class));

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        EntityFormNode subList = new EntityFormNode();
        subList.setId("node-sub-list");
        subList.setNodeType("FIELD");
        subList.setBindingType("NONE");
        subList.setPropsDocument(
                """
                {"fieldCode":"subList","fieldType":"SUB_LIST"}
                """);
        form.setNodes(List.of(subList));

        EntityRelation relation = new EntityRelation();
        relation.setParentFieldCode("reqItemForm");
        relation.setRelationCode("ZDWREQ_reqItemForm");
        when(relationMapper.selectByParentEntityId("entity-1"))
                .thenReturn(List.of(relation));
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));

        Map<String, Object> result = service.applyForm(
                "form-1",
                "ZDWREQ",
                "record-1",
                "edit",
                new LinkedHashMap<>(Map.of(
                        "name", "4444",
                        "reqItemForm", List.of())));

        assertEquals(Map.of("name", "4444"), result);
    }

    /** 当前发布表单包含关系节点时保留空数组，维持用户显式清空子表单的既有语义 */
    @Test
    void keepsRelationDataDeclaredByPublishedForm() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        PublishedFormSubmissionService service =
                service(
                        mock(EntityDefinitionMapper.class),
                        relationMapper,
                        releaseService,
                        mock(UiDataSourceService.class));

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        EntityFormNode relationNode = new EntityFormNode();
        relationNode.setId("node-relation");
        relationNode.setNodeType("REPEATER");
        relationNode.setBindingType("RELATION");
        relationNode.setBindingRef("ZDWREQ_reqItemForm");
        relationNode.setPropsDocument(
                """
                {"fieldCode":"reqItemForm","fieldType":"SUB_FORM"}
                """);
        form.setNodes(List.of(relationNode));

        EntityRelation relation = new EntityRelation();
        relation.setParentFieldCode("reqItemForm");
        relation.setRelationCode("ZDWREQ_reqItemForm");
        when(relationMapper.selectByParentEntityId("entity-1"))
                .thenReturn(List.of(relation));
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));

        Map<String, Object> submitted =
                new LinkedHashMap<>();
        submitted.put("name", "4444");
        submitted.put("reqItemForm", List.of());
        Map<String, Object> result = service.applyForm(
                "form-1",
                "ZDWREQ",
                "record-1",
                "edit",
                submitted);

        assertEquals(submitted, result);
    }

    /** 测试同一业务提交复用绑定幂等键：验证重试时两节点的幂等键与首次一致且节点间互不相同 */
    @Test
    void reusesBindingIdempotencyKeyForSameBusinessSubmission() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        PublishedFormSubmissionService service =
                service(releaseService, dataSourceService);

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        EntityFormNode first = node(
                "node-1",
                "source-1");
        EntityFormNode second = node(
                "node-2",
                "source-1");
        form.setNodes(List.of(first, second));
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));
        when(dataSourceService.execute(
                eq("source-1"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());

        FormSubmissionExecutionContext context =
                executionContext("trace-retry");
        service.applyForm(
                "form-1",
                "expense",
                "record-1",
                "edit",
                Map.of("amount", 88),
                context);
        service.applyForm(
                "form-1",
                "expense",
                "record-1",
                "edit",
                Map.of("amount", 88),
                context);

        ArgumentCaptor<UiDataSourceExecuteRequest> captor =
                ArgumentCaptor.forClass(
                        UiDataSourceExecuteRequest.class);
        verify(dataSourceService, times(4))
                .execute(eq("source-1"), captor.capture());
        String firstAttemptFirstBinding =
                idempotencyKey(captor.getAllValues().get(0));
        String firstAttemptSecondBinding =
                idempotencyKey(captor.getAllValues().get(1));
        String retryFirstBinding =
                idempotencyKey(captor.getAllValues().get(2));
        String retrySecondBinding =
                idempotencyKey(captor.getAllValues().get(3));
        assertEquals(
                firstAttemptFirstBinding,
                retryFirstBinding);
        assertEquals(
                firstAttemptSecondBinding,
                retrySecondBinding);
        assertNotEquals(
                firstAttemptFirstBinding,
                firstAttemptSecondBinding);
    }

    /** 测试数据源失败保持 fail-closed：验证执行抛异常时整体抛出且不吞掉异常 */
    @Test
    void preservesFailClosedDataSourceFailure() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        PublishedFormSubmissionService service =
                service(releaseService, dataSourceService);

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setNodes(List.of(node(
                "node-1",
                "source-1")));
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));
        when(dataSourceService.execute(
                eq("source-1"),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException(
                        "validation failed"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.applyForm(
                        "form-1",
                        "expense",
                        "record-1",
                        "edit",
                        Map.of("amount", 88),
                        executionContext("trace-fail")));

        assertEquals(
                "validation failed",
                exception.getMessage());
    }

    /** 测试解析并执行确切的钉版发布：验证执行的 releaseId/version 与 serverPinnedRelease 标志正确 */
    @Test
    void resolvesAndExecutesTheExactPinnedRelease() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        PublishedFormSubmissionService service =
                service(releaseService, dataSourceService);

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setNodes(List.of(node(
                "node-1",
                "source-1")));
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                "release-7",
                7))
                .thenReturn(resolution(
                        form,
                        "release-7",
                        7,
                        true));
        when(dataSourceService.execute(
                eq("source-1"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());

        service.applyForm(
                "form-1",
                "release-7",
                7,
                "expense",
                "record-1",
                "approve",
                Map.of("amount", 88),
                executionContext("trace-pinned"));

        ArgumentCaptor<UiDataSourceExecuteRequest> captor =
                ArgumentCaptor.forClass(
                        UiDataSourceExecuteRequest.class);
        verify(dataSourceService).execute(
                eq("source-1"),
                captor.capture());
        assertEquals(
                "release-7",
                captor.getValue().getReleaseId());
        assertEquals(
                7,
                captor.getValue().getReleaseVersion());
        assertTrue(captor.getValue().isServerPinnedRelease());
    }

    /** 测试执行表单级 beforeSubmit 绑定：验证表单级数据源被触发且绑定目标为表单所有者 */
    @Test
    void executesFormLevelBeforeSubmitBinding() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        PublishedFormSubmissionService service =
                service(releaseService, dataSourceService);

        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setDataSourceBindingsDocument(
                """
                {"BEFORE_SUBMIT":{
                  "serviceId":"form-source",
                  "operationCode":"beforeSubmit"
                }}
                """);
        form.setNodes(List.of());
        form.setFields(List.of());
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                null,
                null))
                .thenReturn(resolution(
                        form,
                        "release-1",
                        1));
        when(dataSourceService.execute(
                eq("form-source"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("normalized", true));

        Map<String, Object> result = service.applyForm(
                "form-1",
                "expense",
                "record-1",
                "edit",
                Map.of("amount", 88),
                executionContext("trace-form"));

        assertEquals(
                Map.of(
                        "amount", 88,
                        "normalized", true),
                result);
        ArgumentCaptor<UiDataSourceExecuteRequest> captor =
                ArgumentCaptor.forClass(
                        UiDataSourceExecuteRequest.class);
        verify(dataSourceService).execute(
                eq("form-source"),
                captor.capture());
        assertEquals(
                "beforeSubmit",
                captor.getValue().getOperationCode());
        assertEquals("OWNER", captor.getValue().getTargetType());
        assertEquals(null, captor.getValue().getTargetKey());
    }

    /**
     * 测试子表单参数由服务端按父记录重算：验证伪造 params 被忽略、空字段初始化、
     * 非空字段不覆盖、每条子行分别执行 BEFORE_SUBMIT 且幂等键互不相同。
     */
    @Test
    void recursivelyProcessesSubFormRowsWithTrustedParameters() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        PublishedFormSubmissionService service =
                service(
                        definitionMapper,
                        releaseService,
                        dataSourceService);

        EntityForm childForm = new EntityForm();
        childForm.setId("child-form");
        childForm.setEntityId("child-entity");
        childForm.setViewConfig(
                """
                {
                  "inputParameterSchema": {
                    "type": "object",
                    "required": ["projectId"],
                    "properties": {
                      "projectId": {
                        "type": "string",
                        "title": "项目ID"
                      }
                    }
                  }
                }
                """);
        EntityFormField sourceDept = new EntityFormField();
        sourceDept.setFieldCode("source_dept_id");
        sourceDept.setFieldName("来源部门");
        sourceDept.setFieldType("STRING");
        sourceDept.setIsReadonly(0);
        childForm.setFields(List.of(sourceDept));
        EntityFormNode childBeforeSubmit = new EntityFormNode();
        childBeforeSubmit.setId("child-before-submit");
        childBeforeSubmit.setNodeType("FIELD");
        childBeforeSubmit.setDataSourceBindingsDocument(
                """
                {
                  "BEFORE_SUBMIT": {
                    "serviceId": "child-source",
                    "operationCode": "beforeSubmit",
                    "inputMapping": {
                      "projectId": "params.projectId",
                      "sourceDeptId": "data.source_dept_id",
                      "parentRecordId": "parent.recordId",
                      "rowIndex": "row.index"
                    }
                  }
                }
                """);
        childForm.setNodes(List.of(childBeforeSubmit));

        EntityForm parentForm = new EntityForm();
        parentForm.setId("parent-form");
        parentForm.setEntityId("parent-entity");
        EntityFormNode subFormNode = new EntityFormNode();
        subFormNode.setId("members-node");
        subFormNode.setNodeKey("members");
        subFormNode.setNodeType("REPEATER");
        subFormNode.setPropsDocument(
                """
                {
                  "fieldCode": "members",
                  "componentProps": {
                    "subFormConfig": {
                      "childFormId": "child-form",
                      "childFormReleaseId": "child-release",
                      "childFormReleaseVersion": 1,
                      "childEntityId": "child-entity",
                      "childRefFieldCode": "parent_id",
                      "relationType": "ONE_TO_MANY",
                      "parameterContract": {
                        "version": 1,
                        "parameterMapping": {
                          "projectId": "parent.data.project_id"
                        },
                        "fieldInitializationMapping": {
                          "source_dept_id": "parent.data.dept_id"
                        }
                      }
                    }
                  }
                }
                """);
        parentForm.setNodes(List.of(subFormNode));

        when(releaseService.resolveRuntimeFormRelease(
                "parent-form",
                null,
                null))
                .thenReturn(resolution(
                        parentForm,
                        "parent-release",
                        1));
        when(releaseService.resolveRuntimeFormRelease(
                "child-form",
                "child-release",
                1))
                .thenReturn(resolution(
                        childForm,
                        "child-release",
                        1,
                        true));
        EntityDefinition childDefinition =
                new EntityDefinition();
        childDefinition.setId("child-entity");
        childDefinition.setEntityCode("project_member");
        when(definitionMapper.selectById("child-entity"))
                .thenReturn(childDefinition);
        when(dataSourceService.execute(
                eq("child-source"),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    UiDataSourceExecuteRequest request =
                            invocation.getArgument(1);
                    return Map.of(
                            "processedProject",
                            request.getInput().get("projectId"));
                });

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "甲");
        first.put("source_dept_id", "");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("name", "乙");
        second.put("source_dept_id", "manual-dept");
        Map<String, Object> result = service.applyForm(
                "parent-form",
                "parent_entity",
                "parent-record",
                "edit",
                Map.of(
                        "project_id", "project-actual",
                        "dept_id", "dept-from-parent",
                        "params", Map.of(
                                "projectId",
                                "project-forged"),
                        "members", List.of(first, second)),
                executionContext("trace-subform"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members =
                (List<Map<String, Object>>) result.get("members");
        assertEquals(
                "dept-from-parent",
                members.get(0).get("source_dept_id"));
        assertEquals(
                "manual-dept",
                members.get(1).get("source_dept_id"));
        assertEquals(
                "project-actual",
                members.get(0).get("processedProject"));
        assertEquals(
                "project-actual",
                members.get(1).get("processedProject"));

        ArgumentCaptor<UiDataSourceExecuteRequest> captor =
                ArgumentCaptor.forClass(
                        UiDataSourceExecuteRequest.class);
        verify(dataSourceService, times(2))
                .execute(
                        eq("child-source"),
                        captor.capture());
        List<UiDataSourceExecuteRequest> requests =
                captor.getAllValues();
        assertEquals(
                "project-actual",
                requests.get(0).getInput().get("projectId"));
        assertEquals(
                "parent-record",
                requests.get(0).getInput().get(
                        "parentRecordId"));
        assertEquals(
                0,
                requests.get(0).getInput().get("rowIndex"));
        assertEquals(
                1,
                requests.get(1).getInput().get("rowIndex"));
        assertNotEquals(
                idempotencyKey(requests.get(0)),
                idempotencyKey(requests.get(1)));
        assertEquals(
                "beforeSubmit",
                requests.get(0).getOperationCode());
        assertEquals(
                requests.get(0).getInput().get("idempotencyKey"),
                requests.get(0).getServerIdempotencyKey());
    }

    /** 构造带 beforeSubmit 数据源绑定的节点 */
    private EntityFormNode node(
            String id,
            String serviceId) {
        EntityFormNode node = new EntityFormNode();
        node.setId(id);
        node.setDataSourceBindingsDocument(
                "{\"BEFORE_SUBMIT\":{\"serviceId\":\""
                        + serviceId
                        + "\",\"operationCode\":\"beforeSubmit\"}}");
        return node;
    }

    private PublishedFormSubmissionService service(
            UiConfigReleaseService releaseService,
            UiDataSourceService dataSourceService) {
        return service(
                mock(EntityDefinitionMapper.class),
                releaseService,
                dataSourceService);
    }

    private PublishedFormSubmissionService service(
            EntityDefinitionMapper definitionMapper,
            UiConfigReleaseService releaseService,
            UiDataSourceService dataSourceService) {
        return service(
                definitionMapper,
                mock(EntityRelationMapper.class),
                releaseService,
                dataSourceService);
    }

    private PublishedFormSubmissionService service(
            EntityDefinitionMapper definitionMapper,
            EntityRelationMapper relationMapper,
            UiConfigReleaseService releaseService,
            UiDataSourceService dataSourceService) {
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());
        return new PublishedFormSubmissionService(
                definitionMapper,
                mock(EntityFormMapper.class),
                relationMapper,
                releaseService,
                dataSourceService,
                codec,
                new UiDataSourceDefinitionValidator(codec));
    }

    /** 构造携带 traceKey 的表单提交执行上下文 */
    private FormSubmissionExecutionContext executionContext(
            String traceKey) {
        return new FormSubmissionExecutionContext(
                traceKey,
                "ENTITY_UPDATE",
                Map.of(
                        "recordId",
                        "record-1"));
    }

    /** 构造非钉版的已解析表单发布 */
    private ResolvedEntityFormRelease resolution(
            EntityForm form,
            String releaseId,
            Integer releaseVersion) {
        return new ResolvedEntityFormRelease(
                form,
                releaseId,
                releaseVersion);
    }

    /** 构造指定钉版标志的已解析表单发布 */
    private ResolvedEntityFormRelease resolution(
            EntityForm form,
            String releaseId,
            Integer releaseVersion,
            boolean pinned) {
        return new ResolvedEntityFormRelease(
                form,
                releaseId,
                releaseVersion,
                pinned);
    }

    /** 从数据源执行请求中提取幂等键 */
    private String idempotencyKey(
            UiDataSourceExecuteRequest request) {
        return String.valueOf(
                request.getInput().get("idempotencyKey"));
    }
}
