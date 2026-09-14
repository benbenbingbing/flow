package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class UiInterfaceExtensionServiceRevisionTest {

    private static final Pattern REVISION_PARAMETER = Pattern.compile(
            "WHERE.*revision\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}",
            Pattern.CASE_INSENSITIVE);

    @Test
    void updateMatchesPersistedRevisionBeforeIncrementingIt() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition current = definition();
        when(mapper.selectById(current.getId()))
                .thenReturn(current);
        when(mapper.update(isNull(), any()))
                .thenReturn(1);

        UiInterfaceExtensionService service = service(mapper);
        UiExtensionDefinition saved =
                service.save(updateRequest(current));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<UiExtensionDefinition>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        UpdateWrapper<UiExtensionDefinition> update = captor.getValue();
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
        assertEquals("接口扩展（已编辑）", saved.getDisplayName());
    }

    @Test
    void deleteStopsBeforeMutationWhenActiveReleaseStillReferencesService() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiPublishedDataSourceReferenceGuard guard =
                mock(UiPublishedDataSourceReferenceGuard.class);
        UiExtensionDefinition current = definition();
        when(mapper.selectById(current.getId()))
                .thenReturn(current);
        doThrow(new BusinessConflictException(
                "UI_INTERFACE_EXECUTABLE_RELEASE_REFERENCED",
                "仍被线上版本引用"))
                .when(guard).requireNoExecutableReferences(
                        current.getId(), current.getLegacyServiceId());

        BusinessConflictException error = assertThrows(
                BusinessConflictException.class,
                () -> service(mapper, guard).delete(
                        current.getId(), current.getRevision()));

        assertEquals("UI_INTERFACE_EXECUTABLE_RELEASE_REFERENCED",
                error.getErrorCode());
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void updateRejectsUnknownIdInsteadOfCreating() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition current = definition();
        when(mapper.selectById(current.getId())).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service(mapper).save(updateRequest(current)));

        verify(mapper, never()).insert(any(UiExtensionDefinition.class));
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void updateRejectsUiRecordWithInterfacePayload() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition current = definition();
        current.setExtensionType("FORM");
        when(mapper.selectById(current.getId())).thenReturn(current);

        assertThrows(IllegalArgumentException.class,
                () -> service(mapper).save(updateRequest(current)));

        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void updateRejectsChangingStableExtensionKey() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition current = definition();
        when(mapper.selectById(current.getId())).thenReturn(current);
        UiExtensionDefinitionSaveRequest request = updateRequest(current);
        request.setExtensionKey("renamed.interface");

        assertThrows(IllegalArgumentException.class,
                () -> service(mapper).save(request));

        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void updateRejectsMissingExtensionKeyWithValidationError() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition current = definition();
        when(mapper.selectById(current.getId())).thenReturn(current);
        UiExtensionDefinitionSaveRequest request = updateRequest(current);
        request.setExtensionKey(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(mapper).save(request));

        assertTrue(error.getMessage().contains("接口扩展编码不能为空"));
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void updateRejectsExtensionKeyLongerThanDatabaseColumn() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition current = definition();
        current.setExtensionKey("a".repeat(256));
        when(mapper.selectById(current.getId())).thenReturn(current);
        UiExtensionDefinitionSaveRequest request = updateRequest(current);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(mapper).save(request));

        assertTrue(error.getMessage().contains("最多 255"));
        verify(mapper, never()).update(isNull(), any());
    }

    private UiInterfaceExtensionService service(
            UiExtensionDefinitionMapper mapper) {
        return service(
                mapper,
                mock(UiPublishedDataSourceReferenceGuard.class));
    }

    private UiInterfaceExtensionService service(
            UiExtensionDefinitionMapper mapper,
            UiPublishedDataSourceReferenceGuard guard) {
        UiInterfaceExtensionService service = new UiInterfaceExtensionService(
                mapper,
                mock(EntityFormMapper.class),
                mock(EntityListConfigMapper.class),
                mock(EntityDefinitionAccessPolicy.class),
                mock(EntityUiConfigurationPolicy.class),
                mock(SysDictItemService.class),
                mock(UiDataSourceExecutionAccessService.class),
                mock(UiInvocationContextFactory.class),
                mock(UiExtensionDefinitionValidator.class),
                List.<UiDataSourceProvider>of(),
                new JsonDocumentCodec(new ObjectMapper()),
                Runnable::run);
        service.setPublishedReferenceGuard(guard);
        return service;
    }

    private UiExtensionDefinition definition() {
        UiExtensionDefinition definition =
                new UiExtensionDefinition();
        definition.setId("source-1");
        definition.setExtensionType("INTERFACE");
        definition.setSourceCode("source_code");
        definition.setSourceName("接口扩展");
        definition.setSourceType("STATIC_OPTIONS");
        definition.setScopeType("GLOBAL");
        definition.setRevision(1);
        definition.setEnabled(true);
        definition.setDeleted(0);
        return definition;
    }

    private UiExtensionDefinitionSaveRequest updateRequest(
            UiExtensionDefinition current) {
        UiExtensionDefinitionSaveRequest request =
                new UiExtensionDefinitionSaveRequest();
        request.setId(current.getId());
        request.setExpectedRevision(current.getRevision());
        request.setExtensionType("INTERFACE");
        request.setExtensionKey(current.getExtensionKey());
        request.setDisplayName("接口扩展（已编辑）");
        request.setImplementationType(current.getImplementationType());
        request.setScopeType(current.getScopeType());
        request.setImplementationConfig(Map.of(
                "options",
                List.of(Map.of(
                        "label", "验收项",
                        "value", "ok"))));
        request.setExecutionPolicy(Map.of(
                "timeoutMs", 3000,
                "cacheSeconds", 0,
                "failurePolicy", "FAIL"));
        request.setInterfaceKind("READ");
        request.setInterfaceContextType("FORM");
        request.setProviderOperationCode("query");
        request.setInputSchema(Map.of());
        request.setOutputSchema(Map.of());
        request.setStatus("ACTIVE");
        return request;
    }
}
