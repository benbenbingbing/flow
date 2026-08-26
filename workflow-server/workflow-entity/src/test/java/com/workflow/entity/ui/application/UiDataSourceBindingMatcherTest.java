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

    /**
     * 旧 initConfig 即使携带完整接口标识也不能再被识别为统一数据源绑定。
     */
    @Test
    void legacyInitConfigIsNotMatchedAsFormDataSourceBinding() {
        List<Map<String, Object>> owners = List.of(Map.of(
                "formKey", "expense-form",
                "initConfig", Map.of(
                        "FORM_INIT", Map.of(
                                "serviceId", "service-a",
                                "operationCode", "initializeForm"))));

        assertNull(matcher.findForm(
                owners,
                "FORM_INIT",
                "OWNER",
                null,
                "service-a",
                "initializeForm",
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

    @Test
    void publishedCompositionRequiresExactUsageKeyAndOperation() {
        Map<String, Object> snapshot = Map.of(
                "viewCompositions",
                List.of(Map.of(
                        "compositionKey", "project-requirements",
                        "config", Map.of(
                                "specialHandling", Map.of(
                                        "mode", "INTERFACE_SERVICE",
                                        "interfaceService", Map.of(
                                                "serviceId", "service-a",
                                                "operationCode", "resolveRequirements"))))));

        assertEquals(
                "$.release.viewCompositions[0].config.specialHandling.interfaceService",
                matcher.findPublished(
                        "FORM",
                        snapshot,
                        "RELATED_CONTENT_RESOLVE",
                        "COMPOSITION",
                        "project-requirements",
                        "service-a",
                        "resolveRequirements"));
        assertNull(matcher.findPublished(
                "FORM",
                snapshot,
                "RELATED_CONTENT_RESOLVE",
                "COMPOSITION",
                "another-composition",
                "service-a",
                "resolveRequirements"));
        assertNull(matcher.findPublished(
                "FORM",
                snapshot,
                "FORM_INIT",
                "COMPOSITION",
                "project-requirements",
                "service-a",
                "resolveRequirements"));
    }

    @Test
    void publishedCompositionActionRequiresExplicitActionKeyAndOperation() {
        Map<String, Object> snapshot = Map.of(
                "viewCompositions",
                List.of(Map.of(
                        "compositionKey", "project-requirements",
                        "config", Map.of(
                                "specialHandling", Map.of(
                                        "mode", "INTERFACE_SERVICE",
                                        "actionServices", List.of(Map.of(
                                                "actionKey", "calculateRisk",
                                                "serviceId", "service-a",
                                                "operationCode", "calculateRisk")))))));

        assertEquals(
                "$.release.viewCompositions[0].config.specialHandling.actionServices[0]",
                matcher.findPublished(
                        "FORM",
                        snapshot,
                        "RELATED_CONTENT_ACTION",
                        "COMPOSITION_ACTION",
                        "project-requirements::CALCULATERISK",
                        "service-a",
                        "calculateRisk"));
        assertNull(matcher.findPublished(
                "FORM",
                snapshot,
                "RELATED_CONTENT_ACTION",
                "COMPOSITION_ACTION",
                "project-requirements::anotherAction",
                "service-a",
                "calculateRisk"));
        assertNull(matcher.findPublished(
                "FORM",
                snapshot,
                "RELATED_CONTENT_ACTION",
                "COMPOSITION_ACTION",
                "project-requirements::calculateRisk",
                "service-a",
                "anotherOperation"));
    }
}
