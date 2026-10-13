package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.api.request.EntityFormMetadataPatchRequest;
import com.workflow.entity.form.application.EntityFormActionConfigPolicy;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.application.FormUniqueRulePolicy;
import com.workflow.entity.form.application.PublishedFormConditionEvaluator;
import com.workflow.entity.form.application.validation.EntityFormConfigurationValidator;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.list.application.EntityListConfigService;
import com.workflow.entity.list.application.validation.EntityListConfigurationValidator;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.application.validation.StructuredConfigValidator;
import com.workflow.entity.ui.application.validation.UiExtensionDefinitionValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 输入参数设置通过元数据补丁保存时，必须保留独立表单值节点的目标身份。 */
class UiConfigDraftMetadataServiceParameterTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final EntityFormMapper formMapper = mock(EntityFormMapper.class);
    private final EntityFormService formService = mock(EntityFormService.class);
    private final EntityFormConfigurationValidator validator = validator();
    private final UiConfigDraftMetadataService service = new UiConfigDraftMetadataService(
            formMapper,
            mock(EntityListConfigMapper.class),
            formService,
            mock(EntityListConfigService.class),
            validator,
            mock(EntityListConfigurationValidator.class),
            new JsonDocumentCodec(JSON),
            mock(UiAvailableInterfaceService.class));

    /** 文本、多选以及子表值都可以初始化；只读和未绑定实体不改变字段身份。 */
    @Test
    void savesInitializationForAllFormValueNodeTypes() {
        EntityForm form = form(List.of(
                node("textNode", "FIELD", "{\"fieldCode\":\"title\",\"fieldType\":\"STRING\",\"readonly\":true}"),
                node("tags", "FIELD", "{\"fieldType\":\"MULTI_SELECT\"}"),
                node("details", "SUB_FORM", "{}"),
                node("rows", "REPEATER", "{}")));
        when(formService.getById("form-1")).thenReturn(form);
        when(formMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.patchForm("form-1", request("title", "tags", "details", "rows")));

        verify(formMapper).update(isNull(), any(UpdateWrapper.class));
        assertEquals(4, form.getNodes().size(), "保存参数用途不能改写节点树");
    }

    /** 发布快照只有节点而无兼容字段列表时，执行同一套初始化目标校验。 */
    @Test
    void acceptsNodeOnlyPublishedForm() throws Exception {
        EntityForm form = form(List.of(node("tags", null, "{\"readonly\":true}")));
        form.setViewConfig(JSON.writeValueAsString(request("tags").getViewConfig()));

        assertDoesNotThrow(() -> validator.validateForm(form));
    }

    /** 纯布局、已删除节点和不存在字段不能借由初始化绑定写入记录。 */
    @Test
    void rejectsLayoutDeletedAndMissingTargetsBeforeUpdatingMetadata() {
        EntityFormNode deleted = node("removed", "FIELD", "{}");
        deleted.setDeleted(1);
        EntityForm form = form(List.of(
                node("layout", "CONTAINER", "{\"fieldCode\":\"layoutValue\"}"), deleted));
        when(formService.getById("form-1")).thenReturn(form);

        for (String target : List.of("layoutValue", "removed", "missing")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> service.patchForm("form-1", request(target)));
            assertEquals("参数用途字段不可用: " + target, error.getMessage());
        }
        verify(formMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    private static EntityFormConfigurationValidator validator() {
        PublishedFormConditionEvaluator evaluator = new PublishedFormConditionEvaluator(JSON);
        return new EntityFormConfigurationValidator(
                new StructuredConfigValidator(JSON),
                new EntityFormActionConfigPolicy(),
                new UiExtensionDefinitionValidator(new JsonDocumentCodec(JSON)),
                evaluator,
                mock(EntityFieldMapper.class),
                mock(EntityFieldFileItemMapper.class),
                new FormUniqueRulePolicy(JSON, evaluator));
    }

    private static EntityForm form(List<EntityFormNode> nodes) {
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setFormName("参数初始化表单");
        form.setFormKey("parameterForm");
        form.setRevision(1);
        form.setNodes(nodes);
        return form;
    }

    private static EntityFormNode node(String key, String type, String props) {
        EntityFormNode node = new EntityFormNode();
        node.setId(key);
        node.setNodeKey(key);
        node.setNodeType(type);
        node.setBindingType("NONE");
        node.setPropsDocument(props);
        return node;
    }

    private static EntityFormMetadataPatchRequest request(String... targets) {
        EntityFormMetadataPatchRequest request = new EntityFormMetadataPatchRequest();
        request.setExpectedRevision(1);
        request.setViewConfig(Map.of(
                "inputParameterSchema", Map.of("type", "object", "properties", Map.of(
                        "initial", Map.of("type", "array", "title", "初始值", "items", Map.of("type", "string")))),
                "inputParameterBindings", List.of(targets).stream().map(target -> Map.of(
                        "parameter", "initial", "usage", "INITIALIZE", "targetField", target)).toList()));
        return request;
    }
}
