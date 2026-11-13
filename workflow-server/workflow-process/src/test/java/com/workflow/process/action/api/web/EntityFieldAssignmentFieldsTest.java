package com.workflow.process.action.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.infrastructure.adapter.EntityCodeCatalogAdapter;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityPublishHistory;
import com.workflow.process.action.application.EntityFieldAssignmentFlowActionHandler;
import com.workflow.process.action.application.FlowActionFailureStrategyCatalog;
import com.workflow.process.action.application.FlowActionService;
import com.workflow.process.action.application.FlowActionTimingCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 用真实绑定适配器、历史快照解析和 HTTP 序列化验证候选字段，不用预制字段列表替代读取链路。 */
class EntityFieldAssignmentFieldsTest {
    private final EntityDefinitionMapper definitions = mock(EntityDefinitionMapper.class);
    private final EntityPublishHistoryMapper histories = mock(EntityPublishHistoryMapper.class);
    private final EntityPublishHistory history = new EntityPublishHistory();
    private FlowActionController controller;
    private MockMvc http;

    @BeforeEach void setup() {
        var json = new ObjectMapper();
        var entity = new EntityDefinition();
        entity.setEntityCode("legacy_entity");
        when(definitions.findAllByProcessDefinitionId("legacy_process")).thenReturn(List.of(entity));
        history.setEntityId("legacy_entity_id");
        history.setEntityCode("legacy_entity");
        history.setVersion(1);
        when(histories.findLatestByEntityCode("legacy_entity")).thenReturn(history);
        var fields = new EntityFieldAssignmentFlowActionHandler(new EntityCodeCatalogAdapter(definitions),
                new EntityPublishedSnapshotService(histories, json), new EntityFieldValidationRuleService(json),
                mock(SysDictItemService.class), mock(EntityMutationPort.class), json);
        controller = new FlowActionController(mock(FlowActionService.class), mock(FlowActionTimingCatalog.class),
                mock(FlowActionFailureStrategyCatalog.class), fields);
        http = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test void historicalSnapshotWithoutOptionalFlagsReturnsNamedFieldsAndStoredChoiceValues() throws Exception {
        // 旧快照可能缺少 editable/isSystem/isPublished，字段类型也可能是小写，仍按已发布普通字段处理。
        history.setFieldsSnapshot("""
                [{"fieldCode":"legacy_note","fieldName":"历史说明","fieldType":"string"},
                 {"fieldCode":"legacy_result","fieldName":"历史结论","fieldType":"select",
                  "optionsJson":"[{\\"value\\":\\"PASS\\",\\"label\\":\\"通过\\"}]"},
                 {"fieldCode":"status","fieldName":"状态","fieldType":"STRING","isSystem":true},
                 {"fieldCode":"old_items","fieldName":"旧子列表","fieldType":"SUB_FORM_LIST"}]
                """);
        http.perform(get("/api/process-actions/process/legacy_process/assignable-fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fieldCode").value("legacy_note"))
                .andExpect(jsonPath("$.data[0].fieldName").value("历史说明"))
                .andExpect(jsonPath("$.data[0].fieldType").value("STRING"))
                .andExpect(jsonPath("$.data[1].fieldType").value("SELECT"))
                .andExpect(jsonPath("$.data[1].options[0].value").value("PASS"))
                .andExpect(jsonPath("$.data[1].options[0].label").value("通过"));
    }

    @Test void historyContainingOnlyProtectedOrUnsupportedFieldsReturnsExplicitEmptyList() throws Exception {
        history.setFieldsSnapshot("""
                [{"fieldCode":"status","fieldName":"状态","fieldType":"STRING"},
                 {"fieldCode":"owner","fieldName":"人员","fieldType":"USER"},
                 {"fieldCode":"private_note","fieldName":"只读说明","fieldType":"STRING","editable":false}]
                """);
        http.perform(get("/api/process-actions/process/legacy_process/assignable-fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test void missingPublicationAndMissingEntityBindingFailClearlyInsteadOfReturningAnEmptyList() {
        when(histories.findLatestByEntityCode("legacy_entity")).thenReturn(null);
        assertTrue(assertThrows(RuntimeException.class,
                () -> controller.assignableFields("legacy_process")).getMessage().contains("实体未发布"));
        when(definitions.findAllByProcessDefinitionId("legacy_process")).thenReturn(List.of());
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> controller.assignableFields("legacy_process")).getMessage().contains("流程尚未绑定实体"));
    }
}
