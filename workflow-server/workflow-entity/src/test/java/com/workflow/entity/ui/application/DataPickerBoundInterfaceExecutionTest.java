package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.contracts.entity.ui.context.FormInvocationContext;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.api.request.UiBoundExtensionExecuteRequest;
import com.workflow.entity.ui.application.model.UiDataSourceExecutionAuthorization;
import com.workflow.entity.ui.application.validation.UiExtensionDefinitionValidator;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 三个面板入口都复用真实执行链；自动加载不允许 WRITE，也不能绕过发布授权。 */
class DataPickerBoundInterfaceExecutionTest {
    @AfterEach void clearUser() { UserContext.clear(); }

    @ParameterizedTest
    @ValueSource(strings = {"FIELD_PICKER_SCHEMA", "FIELD_PICKER_QUERY", "FIELD_PICKER_RESOLVE"})
    void boundReadsPreserveProtocolAndFailBeforeProviderOnWriteOrPermissionFailure(String usage) {
        UserContext.setCurrentUser("user", "tester");
        var codec = new JsonDocumentCodec(new ObjectMapper());
        var mapper = mock(UiExtensionDefinitionMapper.class);
        var access = mock(UiDataSourceExecutionAccessService.class);
        var contexts = mock(UiInvocationContextFactory.class);
        when(contexts.create(any(), any(), any())).thenReturn(new FormInvocationContext(
                null, null, "f", "form", "表单", "edit", null, "customer", null, null));
        var provider = mock(UiDataSourceProvider.class);
        when(provider.getCode()).thenReturn("customers"); when(provider.getVersion()).thenReturn(1);
        when(provider.getArtifactDigest()).thenReturn("a".repeat(64));
        Map<String, Object> picker = usage.endsWith("QUERY") ? Map.of("pageNum", 1, "pageSize", 20, "keyword", "客户")
                : usage.endsWith("RESOLVE") ? Map.of("values", List.of("001")) : Map.of();
        Map<String, Object> input = Map.of("company", "A", "picker", picker);
        Object output = usage.endsWith("RESOLVE") ? List.of(Map.of("code", "001", "name", "客户")) : Map.of("records", List.of(), "total", 0);
        when(provider.execute(any(), any(), anyMap(), anyMap(), any())).thenAnswer(call -> {
            assertEquals(input, call.getArgument(3)); return output;
        });
        var service = new UiInterfaceExtensionService(mapper, mock(EntityFormMapper.class), mock(EntityListConfigMapper.class),
                mock(EntityDefinitionAccessPolicy.class), mock(EntityUiConfigurationPolicy.class), mock(SysDictItemService.class),
                access, contexts, new UiExtensionDefinitionValidator(codec), List.of(provider), codec, new SimpleAsyncTaskExecutor("picker-test-"));
        var definition = new UiExtensionDefinition();
        definition.setId("source"); definition.setExtensionType("INTERFACE"); definition.setEnabled(true);
        definition.setImplementationType("REGISTERED_PROVIDER"); definition.setProviderCode("customers"); definition.setProviderOperationCode("read");
        definition.setInterfaceContextType("FORM"); definition.setInterfaceKind("READ");
        definition.setOutputSchemaDocument(usage.endsWith("RESOLVE") ? "{\"type\":\"array\"}" : "{\"type\":\"object\"}");
        when(mapper.selectById("source")).thenReturn(definition);
        var user = new SysUser(); user.setId("user"); user.setUsername("tester");
        when(access.authorizePublished(any(), any())).thenReturn(new UiDataSourceExecutionAuthorization(
                false, "FORM", "f", "release", 1, "$.nodes[0]", usage, "e", "customer", null, user,
                new DataScopePlan(true, "1=1", Map.of(), List.of(), List.of(), "all", 1), Map.of(), null));
        var request = new UiBoundExtensionExecuteRequest();
        request.setOwnerType("FORM"); request.setOwnerId("f"); request.setConfigHash("hash"); request.setBindingCode(usage);
        request.setTargetType("FIELD"); request.setTargetKey("customer"); request.setExtensionId("source"); request.setInput(input);
        assertEquals(output, service.executeBoundOperation(request));
        definition.setInterfaceKind("WRITE");
        assertEquals("FIELD_PICKER_READ_REQUIRED", assertThrows(BusinessForbiddenException.class, () -> service.executeBoundOperation(request)).getErrorCode());
        definition.setInterfaceKind("READ");
        when(access.authorizePublished(any(), any())).thenThrow(new BusinessForbiddenException("DENIED", "无权读取"));
        assertThrows(BusinessForbiddenException.class, () -> service.executeBoundOperation(request));
        verify(provider, times(1)).execute(any(), any(), anyMap(), anyMap(), any());
    }
}
