package com.workflow.entity.list.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.list.application.validation.ListCellActionMappingPolicy;
import com.workflow.entity.list.api.request.EntityListActionSaveRequest;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListActionMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListAction;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 覆盖功能映射的保存/重载契约与发布前冲突校验，不连接业务数据库。 */
class ListCellActionMappingPolicyTest {
    @Test
    void preservesMappingInSavedRuntimeAndReleaseButtonsAndClearsIt() {
        EntityListActionMapper actions = mock(EntityListActionMapper.class);
        EntityListConfigMapper lists = mock(EntityListConfigMapper.class);
        EntityListConfig list = new EntityListConfig();
        list.setId("list");
        when(lists.selectById("list")).thenReturn(list);
        when(actions.findByListAndPosition("list", "ROW")).thenReturn(List.of());
        JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
        EntityListRelationalConfigService service = new EntityListRelationalConfigService(actions, lists, null, null, codec, null);
        EntityListActionSaveRequest request = new EntityListActionSaveRequest();
        request.setPosition("ROW");
        request.setButtonType("built-in");
        request.setButtonKey("view");
        request.setButtonLabel("查看");
        request.setActionParams(Map.of("mappedFieldCode", "hidden_field", "hideWhenMapped", true));

        EntityListAction saved = service.createAction("list", request);
        when(actions.findByListAndPosition("list", "ROW")).thenReturn(List.of(saved));
        Map<String, Object> runtime = service.findActions("list", "ROW").get(0);
        assertEquals("hidden_field", runtime.get("mappedFieldCode"));
        assertEquals(true, runtime.get("hideWhenMapped"));
        Map<String, Object> released = EntityListRelationalConfigService.normalizeReleaseActionPersistenceDefaults(
                "list", "ROW", List.of(runtime), List.of()).get(0);
        assertEquals("hidden_field", released.get("mappedFieldCode"));
        assertEquals(true, released.get("hideWhenMapped"));

        // 清空映射通过完整替换扩展参数实现，不能把旧隐藏配置合并回来。
        request.setActionParams(Map.of());
        ReflectionTestUtils.invokeMethod(service, "applyAction", saved, request);
        assertFalse(service.findActions("list", "ROW").get(0).containsKey("mappedFieldCode"));
        assertFalse(service.findActions("list", "ROW").get(0).containsKey("hideWhenMapped"));
    }

    @Test
    void rejectsAmbiguousMappingsIncludingDisabledButtonsBeforePublish() {
        Map<String, Object> view = Map.of("type", "built-in", "key", "view", "mappedFieldCode", "name");
        Map<String, Object> edit = Map.of("type", "built-in", "key", "edit", "mappedFieldCode", "name", "enabled", false);
        assertDoesNotThrow(() -> ListCellActionMappingPolicy.validateButtons("ROW", List.of(view)));
        assertThrows(IllegalArgumentException.class, () -> ListCellActionMappingPolicy.validateButtons("ROW", List.of(view, edit)));
        assertThrows(IllegalArgumentException.class, () -> ListCellActionMappingPolicy.validateButtons("TOOLBAR", List.of(view)));
    }

    @Test
    void acceptsPlatformDispatchModesAndRejectsComponentsAndInvalidTypes() {
        Map<String, Object> params = Map.of("mappedFieldCode", "name", "hideWhenMapped", false);
        for (String mode : List.of("handler", "event", "open-form", "open-list", "open-related-content")) {
            assertDoesNotThrow(() -> ListCellActionMappingPolicy.validate("ROW", "custom", "action", mode, params));
        }
        assertThrows(IllegalArgumentException.class,
                () -> ListCellActionMappingPolicy.validate("ROW", "custom", "action", "component", params));
        assertThrows(IllegalArgumentException.class,
                () -> ListCellActionMappingPolicy.validate("ROW", "built-in", "view", "", Map.of("mappedFieldCode", List.of("name"))));
        assertThrows(IllegalArgumentException.class,
                () -> ListCellActionMappingPolicy.validate("ROW", "built-in", "view", "", Map.of("hideWhenMapped", "true")));
        assertDoesNotThrow(() -> ListCellActionMappingPolicy.validate("ROW", "custom", "component", "component", Map.of()));
    }
}
