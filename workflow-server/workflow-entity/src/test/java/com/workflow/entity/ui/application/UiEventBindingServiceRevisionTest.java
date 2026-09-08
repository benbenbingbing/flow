package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.request.UiEventBindingSaveRequest;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiEventBindingServiceRevisionTest {

    private static final Pattern REVISION_PARAMETER = Pattern.compile(
            "WHERE.*revision\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}",
            Pattern.CASE_INSENSITIVE);

    @Test
    void updateMatchesPersistedRevisionBeforeIncrementingIt() {
        UiEventBindingMapper mapper =
                mock(UiEventBindingMapper.class);
        UiEventBinding current = binding();
        when(mapper.selectById(current.getId()))
                .thenReturn(current);
        when(mapper.update(isNull(), any()))
                .thenReturn(1);

        UiEventBinding saved =
                service(mapper).save(updateRequest(current));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<UiEventBinding>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        UpdateWrapper<UiEventBinding> update = captor.getValue();
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
        assertEquals("REPLACE", saved.getInheritanceMode());
    }

    @Test
    void entitySharedEventAllowsOneReplacePerProjectedContext() {
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        when(dataSourceService.operations("form-source"))
                .thenReturn(List.of(operation("form-op", "FORM")));
        when(dataSourceService.operations("list-source"))
                .thenReturn(List.of(operation("list-op", "LIST")));
        UiEventBindingSaveRequest request = sharedRequest(List.of(
                step("form-source", "form-op", 10),
                step("list-source", "list-op", 20)));

        assertDoesNotThrow(() ->
                service(mapper, dataSourceService).save(request));
    }

    @Test
    void entitySharedEventRejectsTwoReplacesInSameProjectedContext() {
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiDataSourceService dataSourceService =
                mock(UiDataSourceService.class);
        when(dataSourceService.operations("form-source-a"))
                .thenReturn(List.of(operation("form-op-a", "FORM")));
        when(dataSourceService.operations("form-source-b"))
                .thenReturn(List.of(operation("form-op-b", "FORM")));
        UiEventBindingSaveRequest request = sharedRequest(List.of(
                step("form-source-a", "form-op-a", 10),
                step("form-source-b", "form-op-b", 20)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(mapper, dataSourceService).save(request));

        assertTrue(error.getMessage().contains("FORM"));
    }

    @Test
    void draftResolutionAndSnapshotApiProjectSharedEntityStepsByContext() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonDocumentCodec codec = new JsonDocumentCodec(objectMapper);
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        UiDataSourceDefinitionMapper sourceMapper =
                mock(UiDataSourceDefinitionMapper.class);
        EntityDefinitionMapper definitionMapper =
                mock(EntityDefinitionMapper.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityListConfigMapper listMapper =
                mock(EntityListConfigMapper.class);
        UiEventBindingSnapshotService snapshotService =
                new UiEventBindingSnapshotService(
                        mapper, sourceMapper, codec);
        UiEventBindingService service = new UiEventBindingService(
                mapper,
                mock(UiConfigReleaseMapper.class),
                definitionMapper,
                formMapper,
                listMapper,
                mock(EntityDefinitionAccessPolicy.class),
                mock(UiConfigurationAccessService.class),
                mock(UiDataSourceService.class),
                snapshotService,
                mock(UiConfigReleaseService.class),
                codec,
                objectMapper);
        UiEventBinding binding = binding();
        binding.setStepsDocument(codec.write(
                List.of(
                        Map.of(
                                "serviceId", "source-mixed",
                                "operationCode", "form-op",
                                "order", 10),
                        Map.of(
                                "serviceId", "source-mixed",
                                "operationCode", "list-op",
                                "order", 20)),
                "测试事件步骤"));
        UiDataSourceDefinition source = new UiDataSourceDefinition();
        source.setId("source-mixed");
        source.setOperationsDocument(codec.write(
                List.of(
                        operation("form-op", "FORM"),
                        operation("list-op", "LIST")),
                "测试接口操作"));
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setEntityCode("acceptance");
        EntityListConfig list = new EntityListConfig();
        list.setId("list-1");
        list.setEntityId("entity-1");
        list.setEntityCode("acceptance");
        list.setListKey("default");
        when(mapper.findForSnapshot(
                "FORM", "form-1", "entity-1"))
                .thenReturn(List.of(binding));
        when(mapper.findForSnapshot(
                "LIST", "list-1", "entity-1"))
                .thenReturn(List.of(binding));
        when(sourceMapper.selectById("source-mixed"))
                .thenReturn(source);
        when(formMapper.selectById("form-1")).thenReturn(form);
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(entity);
        when(listMapper.selectById("list-1")).thenReturn(list);

        List<Map<String, Object>> formSnapshot = service.snapshotBindings(
                "form", "form-1", "entity-1");
        List<Map<String, Object>> listSnapshot = service.snapshotBindings(
                "list", "list-1", "entity-1");
        Map<String, Object> formResolved = service.resolveDraft(
                "form", "form-1", "DETAIL_LOAD");
        Map<String, Object> listResolved = service.resolveDraft(
                "list", "list-1", "DETAIL_LOAD");

        assertEquals(List.of("form-op"), operationCodes(
                formSnapshot.get(0).get("steps")));
        assertEquals(List.of("list-op"), operationCodes(
                listSnapshot.get(0).get("steps")));
        assertEquals(List.of("form-op"), operationCodes(
                formResolved.get("steps")));
        assertEquals(List.of("list-op"), operationCodes(
                listResolved.get("steps")));
        assertEquals("INHERITED", formResolved.get("source"));
        assertEquals("INHERITED", listResolved.get("source"));
    }

    private UiEventBindingService service(
            UiEventBindingMapper mapper) {
        return service(mapper, mock(UiDataSourceService.class));
    }

    private UiEventBindingService service(
            UiEventBindingMapper mapper,
            UiDataSourceService dataSourceService) {
        return new UiEventBindingService(
                mapper,
                mock(UiConfigReleaseMapper.class),
                mock(EntityDefinitionMapper.class),
                mock(EntityFormMapper.class),
                mock(EntityListConfigMapper.class),
                mock(EntityDefinitionAccessPolicy.class),
                mock(UiConfigurationAccessService.class),
                dataSourceService,
                mock(UiEventBindingSnapshotService.class),
                mock(UiConfigReleaseService.class),
                new JsonDocumentCodec(new ObjectMapper()),
                new ObjectMapper());
    }

    private UiEventBindingSaveRequest sharedRequest(
            List<Map<String, Object>> steps) {
        UiEventBindingSaveRequest request = new UiEventBindingSaveRequest();
        request.setOwnerType("ENTITY");
        request.setOwnerId("entity-1");
        request.setTargetType("OWNER");
        request.setEventCode("DETAIL_LOAD");
        request.setInheritanceMode("INHERIT");
        request.setSteps(steps);
        request.setEnabled(true);
        return request;
    }

    private Map<String, Object> step(
            String serviceId,
            String operationCode,
            int order) {
        return Map.of(
                "serviceId", serviceId,
                "operationCode", operationCode,
                "strategy", "REPLACE",
                "failurePolicy", "STOP",
                "order", order);
    }

    private Map<String, Object> operation(
            String code,
            String contextType) {
        return Map.of(
                "code", code,
                "contextType", contextType);
    }

    private List<String> operationCodes(Object value) {
        if (!(value instanceof List<?> steps)) {
            return List.of();
        }
        return steps.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(step -> String.valueOf(step.get("operationCode")))
                .toList();
    }

    private UiEventBinding binding() {
        UiEventBinding binding = new UiEventBinding();
        binding.setId("binding-1");
        binding.setOwnerType("ENTITY");
        binding.setOwnerId("entity-1");
        binding.setTargetType("OWNER");
        binding.setTargetKey("");
        binding.setEventCode("DETAIL_LOAD");
        binding.setInheritanceMode("INHERIT");
        binding.setRevision(1);
        binding.setEnabled(true);
        binding.setDeleted(0);
        return binding;
    }

    private UiEventBindingSaveRequest updateRequest(
            UiEventBinding current) {
        UiEventBindingSaveRequest request =
                new UiEventBindingSaveRequest();
        request.setId(current.getId());
        request.setExpectedRevision(current.getRevision());
        request.setOwnerType(current.getOwnerType());
        request.setOwnerId(current.getOwnerId());
        request.setTargetType(current.getTargetType());
        request.setTargetKey(current.getTargetKey());
        request.setEventCode(current.getEventCode());
        request.setInheritanceMode("REPLACE");
        request.setSteps(List.of());
        request.setEnabled(true);
        return request;
    }
}
