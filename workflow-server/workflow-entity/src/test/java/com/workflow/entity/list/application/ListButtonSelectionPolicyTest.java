package com.workflow.entity.list.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.list.api.request.EntityListActionSaveRequest;
import com.workflow.entity.list.application.validation.ListButtonSelectionPolicy;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListActionMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListAction;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 选择要求的持久化、旧配置默认行为与数量边界；不连接业务数据库。 */
class ListButtonSelectionPolicyTest {
    @Test
    void validatesCountsAndRejectsUnsupportedConfigurations() {
        for (String requirement : List.of("NONE", "SINGLE", "AT_LEAST_ONE")) {
            Map<String, Object> button = Map.of("type", "custom", "key", "archive", "selectionRequirement", requirement);
            ListButtonSelectionPolicy.validateButtons("TOOLBAR", List.of(button));
            for (int count : List.of(0, 1, 2)) {
                boolean allowed = "NONE".equals(requirement) || ("SINGLE".equals(requirement) ? count == 1 : count > 0);
                if (allowed) assertDoesNotThrow(() -> ListButtonSelectionPolicy.requireCount(button, count));
                else assertThrows(BusinessForbiddenException.class, () -> ListButtonSelectionPolicy.requireCount(button, count));
            }
            assertThrows(IllegalArgumentException.class, () -> ListButtonSelectionPolicy.validateButtons("ROW", List.of(button)));
        }
        for (Object invalid : List.of("MULTIPLE", "", 1, true)) {
            assertThrows(IllegalArgumentException.class, () -> ListButtonSelectionPolicy.validateButtons("TOOLBAR",
                    List.of(Map.of("type", "custom", "selectionRequirement", invalid))));
        }
        assertEquals("NONE", ListButtonSelectionPolicy.requirement(Map.of("type", "custom")));
        assertEquals("AT_LEAST_ONE", ListButtonSelectionPolicy.requirement(Map.of("key", "batchDelete")));
        assertEquals("SINGLE", ListButtonSelectionPolicy.requirement(Map.of("type", "custom", "customMode", "open-related-content")));
        Map<String, Object> mappingButton = Map.of("type", "custom", "customMode", "open-list",
                "selectionRequirement", "NONE", "parameterMappings", List.of(Map.of("sourceType", "RECORD_ID")));
        assertEquals("SINGLE", ListButtonSelectionPolicy.requirement(mappingButton));
        assertThrows(IllegalArgumentException.class, () -> ListButtonSelectionPolicy.validateButtons("TOOLBAR", List.of(mappingButton)));
        assertEquals("NONE", ListButtonSelectionPolicy.requirement(Map.of("type", "custom", "customMode", "open-list", "selectionMode", "SINGLE")));
    }

    @Test
    void preservesSelectionRequirementThroughSaveReloadAndReleaseProjection() {
        EntityListActionMapper actions = mock(EntityListActionMapper.class);
        EntityListConfigMapper lists = mock(EntityListConfigMapper.class);
        EntityListConfig list = new EntityListConfig();
        list.setId("list");
        when(lists.selectById("list")).thenReturn(list);
        when(actions.findByListAndPosition("list", "TOOLBAR")).thenReturn(List.of());
        JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
        EntityListRelationalConfigService service = new EntityListRelationalConfigService(actions, lists, null, null, codec, null);
        EntityListActionSaveRequest request = new EntityListActionSaveRequest();
        request.setPosition("TOOLBAR");
        request.setButtonType("custom");
        request.setCustomMode("event");
        request.setButtonKey("archive");
        request.setButtonLabel("归档");
        request.setActionParams(Map.of("selectionRequirement", "AT_LEAST_ONE"));
        EntityListAction saved = service.createAction("list", request);
        when(actions.findByListAndPosition("list", "TOOLBAR")).thenReturn(List.of(saved));
        Map<String, Object> runtime = service.findActions("list", "TOOLBAR").get(0);
        assertEquals("AT_LEAST_ONE", runtime.get("selectionRequirement"));
        Map<String, Object> release = EntityListRelationalConfigService.normalizeReleaseActionPersistenceDefaults(
                "list", "TOOLBAR", List.of(runtime), List.of()).get(0);
        assertEquals("AT_LEAST_ONE", release.get("selectionRequirement"));
        request.setActionParams(Map.of("selectionRequirement", "INVALID"));
        assertThrows(IllegalArgumentException.class, () -> service.createAction("list", request));
    }
}
