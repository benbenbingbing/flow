package com.workflow.entity.data.application;

import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.context.EntityFormRuntimeContext;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.application.EntityListPublishedRuntimeService;
import com.workflow.entity.list.application.EntityListRuntimeContext;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.entity.ui.application.UiEventRuntimeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 详情实际读取的路径记录必须进入关联授权，不能被缺失或伪造的客户端上下文替换。 */
class RelatedListDetailContextTest {
    private final EntityDataActionService service = mock(EntityDataActionService.class, CALLS_REAL_METHODS);
    private final UiEventRuntimeService events = mock(UiEventRuntimeService.class);

    RelatedListDetailContextTest() {
        var definitions = mock(EntityDefinitionMapper.class);
        var forms = mock(EntityFormMapper.class);
        var actions = mock(EntityListActionConfigService.class);
        var lists = mock(EntityListPublishedRuntimeService.class);
        ReflectionTestUtils.setField(service, "definitionMapper", definitions);
        ReflectionTestUtils.setField(service, "formMapper", forms);
        ReflectionTestUtils.setField(service, "actionConfigService", actions);
        ReflectionTestUtils.setField(service, "publishedListRuntimeService", lists);
        ReflectionTestUtils.setField(service, "capabilityService", mock(EntityActionCapabilityService.class));
        ReflectionTestUtils.setField(service, "eventRuntimeService", events);
        EntityDefinition definition = new EntityDefinition();
        definition.setId("child-entity"); definition.setEntityCode("CHILD");
        definition.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(definitions.findByEntityCode("CHILD")).thenReturn(Optional.of(definition));
        when(definitions.selectById("child-entity")).thenReturn(definition);
        EntityForm form = new EntityForm(); form.setId("child-form"); form.setEntityId("child-entity");
        when(forms.selectById("child-form")).thenReturn(form);
        EntityListConfig list = new EntityListConfig(); list.setId("child-list"); list.setListKey("items");
        when(actions.resolveListConfig("CHILD", "items")).thenReturn(list);
        when(lists.resolveConfig(eq(list), any(), anyMap())).thenReturn(list);
        UiEventExecutionResult result = new UiEventExecutionResult();
        EntityDataDTO row = new EntityDataDTO(); row.setId("actual-child"); result.setData(row);
        when(events.execute(any(), any())).thenReturn(result);
    }

    private UiEventExecuteRequest load(Map<String, Object> context) {
        assertEquals("actual-child", service.getDetail("CHILD", "actual-child", "items", "child-form",
                EntityListRuntimeContext.current(), new EntityFormRuntimeContext("hash", context)).getId());
        ArgumentCaptor<UiEventExecuteRequest> request = ArgumentCaptor.forClass(UiEventExecuteRequest.class);
        verify(events).execute(request.capture(), any());
        return request.getValue();
    }

    @Test void omittedContextRecordUsesDetailPathAndKeepsParentSource() {
        Map<String, Object> source = Map.of("ownerType", "FORM", "ownerId", "parent-form",
                "compositionKey", "children", "recordId", "parent-record");
        Map<String, Object> context = Map.of("compositionContext", source);
        var request = load(context);
        assertEquals("actual-child", request.getRuntimeContext().get("recordId"));
        assertEquals(source, request.getRuntimeContext().get("compositionContext"));
        assertFalse(context.containsKey("recordId"));
    }

    @Test void claimedContextRecordCannotReplaceDetailPath() {
        assertEquals("actual-child", load(Map.of("recordId", "claimed-child")).getRuntimeContext().get("recordId"));
    }

    @Test void nestedFormKeepsRootAndChildRecordCoordinates() {
        Map<String, Object> context = Map.of("recordId", "root-record", "rootFormId", "root-form",
                "componentPath", List.of(Map.of("nodeKey", "children", "recordId", "actual-child")),
                "childRecordId", "actual-child");
        Map<String, Object> actual = load(context).getRuntimeContext();
        context.forEach((key, value) -> assertEquals(value, actual.get(key)));
        assertEquals("CHILD", actual.get("entityCode"));
    }
}
