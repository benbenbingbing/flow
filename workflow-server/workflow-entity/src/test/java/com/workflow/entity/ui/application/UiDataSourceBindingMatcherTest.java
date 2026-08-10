package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class UiDataSourceBindingMatcherTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();
    private final UiDataSourceBindingMatcher matcher =
            new UiDataSourceBindingMatcher(
                    mock(UiEventBindingMapper.class),
                    new JsonDocumentCodec(objectMapper),
                    objectMapper);

    @Test
    void formBindingMatchesStableTargetAndOperationTogether() throws Exception {
        List<Map<String, Object>> owners = List.of(
                Map.of(
                        "formKey", "expense-form",
                        "dataSourceBindings", Map.of()),
                Map.of(
                        "nodeKey", "approver-node",
                        "propsDocument",
                        objectMapper.writeValueAsString(
                                Map.of("fieldCode", "approverId")),
                        "dataSourceBindings",
                        Map.of(
                                "FIELD_OPTIONS",
                                Map.of(
                                        "serviceId", "service-a",
                                        "operationCode", "queryApprovers"))));

        assertEquals(
                "$.draft.form[1].dataSourceBindings.FIELD_OPTIONS",
                matcher.findForm(
                        owners,
                        "FIELD_OPTIONS",
                        "FIELD",
                        "approverId",
                        "service-a",
                        "queryApprovers",
                        "$.draft.form"));
        assertNull(matcher.findForm(
                owners,
                "FIELD_OPTIONS",
                "FIELD",
                "approverId",
                "service-a",
                "otherOperation",
                "$.draft.form"));
        assertNull(matcher.findForm(
                owners,
                "FIELD_OPTIONS",
                "FIELD",
                "reviewerId",
                "service-a",
                "queryApprovers",
                "$.draft.form"));
    }

    @Test
    void listColumnAndListQueryRequireConfiguredOperation() {
        Map<String, Object> list = Map.of(
                "queryDataSourceId", "query-service",
                "queryOperationCode", "query-page");
        List<Map<String, Object>> fields = List.of(
                Map.of(
                        "fieldCode", "riskLevel",
                        "dataSourceId", "service-a",
                        "dataSourceOperationCode", "calculateRisk"));

        assertEquals(
                "$.release.list.fields[0].dataSourceId",
                matcher.findList(
                        list,
                        fields,
                        "LIST_COLUMN",
                        "COLUMN",
                        "riskLevel",
                        "service-a",
                        "calculateRisk",
                        "$.release.list"));
        assertEquals(
                "$.release.list.queryDataSourceId",
                matcher.findList(
                        list,
                        fields,
                        "LIST_QUERY",
                        "OWNER",
                        null,
                        "query-service",
                        "query-page",
                        "$.release.list"));
        assertNull(matcher.findList(
                list,
                fields,
                "LIST_QUERY",
                "OWNER",
                null,
                "query-service",
                "other-operation",
                "$.release.list"));
    }
}
