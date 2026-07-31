package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.contracts.integration.IntegrationConnector;
import com.workflow.contracts.ui.UiDataSourceProvider;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.api.request.UiDataSourceSaveRequest;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiDataSourceServiceRevisionTest {

    private static final Pattern REVISION_PARAMETER = Pattern.compile(
            "WHERE.*revision\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}",
            Pattern.CASE_INSENSITIVE);

    @Test
    void updateMatchesPersistedRevisionBeforeIncrementingIt() {
        UiDataSourceDefinitionMapper mapper =
                mock(UiDataSourceDefinitionMapper.class);
        UiDataSourceDefinition current = definition();
        when(mapper.selectById(current.getId()))
                .thenReturn(current);
        when(mapper.update(isNull(), any()))
                .thenReturn(1);

        UiDataSourceService service = service(mapper);
        UiDataSourceDefinition saved =
                service.save(updateRequest(current));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<UiDataSourceDefinition>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        UpdateWrapper<UiDataSourceDefinition> update = captor.getValue();
        Matcher matcher = REVISION_PARAMETER.matcher(
                update.getCustomSqlSegment());
        assertTrue(matcher.find());
        assertEquals(
                1,
                update.getParamNameValuePairs().get(matcher.group(1)),
                () -> update.getCustomSqlSegment()
                        + " "
                        + update.getParamNameValuePairs());
        assertEquals(2, saved.getRevision());
        assertEquals("接口服务（已编辑）", saved.getSourceName());
    }

    private UiDataSourceService service(
            UiDataSourceDefinitionMapper mapper) {
        return new UiDataSourceService(
                mapper,
                mock(EntityFormMapper.class),
                mock(EntityListConfigMapper.class),
                mock(EntityDefinitionAccessPolicy.class),
                mock(EntityUiConfigurationPolicy.class),
                mock(EntityDataDynamicService.class),
                mock(SysDictItemService.class),
                mock(UiDataSourceExecutionAccessService.class),
                mock(UiDataSourceDefinitionValidator.class),
                List.<UiDataSourceProvider>of(),
                List.<IntegrationConnector>of(),
                new JsonDocumentCodec(new ObjectMapper()),
                Runnable::run);
    }

    private UiDataSourceDefinition definition() {
        UiDataSourceDefinition definition =
                new UiDataSourceDefinition();
        definition.setId("source-1");
        definition.setSourceCode("source_code");
        definition.setSourceName("接口服务");
        definition.setSourceType("STATIC_OPTIONS");
        definition.setScopeType("GLOBAL");
        definition.setRevision(1);
        definition.setEnabled(true);
        definition.setDeleted(0);
        return definition;
    }

    private UiDataSourceSaveRequest updateRequest(
            UiDataSourceDefinition current) {
        UiDataSourceSaveRequest request =
                new UiDataSourceSaveRequest();
        request.setId(current.getId());
        request.setExpectedRevision(current.getRevision());
        request.setSourceCode(current.getSourceCode());
        request.setSourceName("接口服务（已编辑）");
        request.setSourceType(current.getSourceType());
        request.setScopeType(current.getScopeType());
        request.setConfig(Map.of(
                "options",
                List.of(Map.of(
                        "label", "验收项",
                        "value", "ok"))));
        request.setInputSchema(Map.of());
        request.setOutputSchema(Map.of());
        request.setExecutionPolicy(Map.of(
                "timeoutMs", 3000,
                "cacheSeconds", 0,
                "failurePolicy", "FAIL"));
        request.setOperations(List.of(Map.of(
                "code", "query",
                "name", "查询数据",
                "kind", "READ",
                "config", Map.of(),
                "inputSchema", Map.of(),
                "outputSchema", Map.of())));
        request.setEnabled(true);
        return request;
    }
}
