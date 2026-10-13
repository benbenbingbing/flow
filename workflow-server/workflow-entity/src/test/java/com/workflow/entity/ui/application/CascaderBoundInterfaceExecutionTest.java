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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 在真实绑定执行和 Schema 校验链中验证级联协议，不为级联建立独立后端通路。 */
class CascaderBoundInterfaceExecutionTest {
    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALL", "CHILDREN"})
    void boundReadPreservesInputAndNodesAndStillRequiresPublishedAuthorization(String mode) {
        UserContext.setCurrentUser("user-1", "tester");
        JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
        UiExtensionDefinitionMapper mapper = mock(UiExtensionDefinitionMapper.class);
        UiDataSourceExecutionAccessService access = mock(UiDataSourceExecutionAccessService.class);
        UiInvocationContextFactory contexts = mock(UiInvocationContextFactory.class);
        when(contexts.create(any(), any(), any())).thenReturn(new FormInvocationContext(
                null, null, "form-1", "form", "测试表单", "edit", null, "region", null, null));
        UiDataSourceProvider provider = mock(UiDataSourceProvider.class);
        when(provider.getCode()).thenReturn("region-provider");
        when(provider.getVersion()).thenReturn(1);
        when(provider.getArtifactDigest()).thenReturn("a".repeat(64));
        Map<String, Object> leaf = Map.of("value", 0, "label", "市", "leaf", true, "disabled", false);
        List<Map<String, Object>> nodes = "ALL".equals(mode)
                ? List.of(Map.of("value", "province", "label", "省", "leaf", false, "children", List.of(leaf)))
                : List.of(leaf);
        Map<String, Object> cascade = new LinkedHashMap<>();
        cascade.put("loadMode", mode);
        cascade.put("parentValue", "ALL".equals(mode) ? null : "province");
        cascade.put("path", "ALL".equals(mode) ? List.of() : List.of("province"));
        cascade.put("level", "ALL".equals(mode) ? 0 : 1);
        Map<String, Object> input = Map.of("cascader", cascade);
        when(provider.execute(any(), any(), anyMap(), anyMap(), any())).thenAnswer(call -> {
            assertEquals(input, call.getArgument(3));
            return nodes;
        });
        UiInterfaceExtensionService service = new UiInterfaceExtensionService(mapper,
                mock(EntityFormMapper.class), mock(EntityListConfigMapper.class),
                mock(EntityDefinitionAccessPolicy.class), mock(EntityUiConfigurationPolicy.class),
                mock(SysDictItemService.class), access, contexts, new UiExtensionDefinitionValidator(codec),
                List.of(provider), codec, new SimpleAsyncTaskExecutor("cascader-test-"));
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId("source-1");
        definition.setExtensionType("INTERFACE");
        definition.setEnabled(true);
        definition.setImplementationType("REGISTERED_PROVIDER");
        definition.setProviderCode("region-provider");
        definition.setProviderOperationCode("query");
        definition.setInterfaceContextType("FORM");
        definition.setInterfaceKind("READ");
        definition.setOutputSchemaDocument(codec.write(Map.of("type", "array", "items", Map.of(
                "type", "object", "required", List.of("value", "label"), "properties", Map.of(
                        "label", Map.of("type", "string"), "leaf", Map.of("type", "boolean"),
                        "children", Map.of("type", "array")))), "级联输出契约"));
        when(mapper.selectById("source-1")).thenReturn(definition);
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setUsername("tester");
        when(access.authorizePublished(any(), any())).thenReturn(new UiDataSourceExecutionAuthorization(
                false, "FORM", "form-1", "release-1", 1, "$.nodes[0]", "FIELD_OPTIONS", "entity-1",
                "expense", null, user, new DataScopePlan(true, "1=1", Map.of(), List.of(), List.of(), "all", 1),
                Map.of(), null));
        UiBoundExtensionExecuteRequest request = new UiBoundExtensionExecuteRequest();
        request.setOwnerType("FORM");
        request.setOwnerId("form-1");
        request.setConfigHash("form-hash");
        request.setBindingCode("FIELD_OPTIONS");
        request.setTargetType("FIELD");
        request.setTargetKey("region");
        request.setExtensionId("source-1");
        request.setInput(input);
        assertEquals(nodes, service.executeBoundOperation(request));

        // 界面发布后若有人把接口改成 WRITE，自动加载/回显仍只能读，不能运行有副作用的实现。
        definition.setInterfaceKind("WRITE");
        assertEquals("FIELD_OPTIONS_READ_REQUIRED", assertThrows(BusinessForbiddenException.class,
                () -> service.executeBoundOperation(request)).getErrorCode());
        definition.setInterfaceKind("READ");
        // 拒绝发布绑定时必须在执行 Provider 前终止，级联父路径不能代替访问授权。
        when(access.authorizePublished(any(), any())).thenThrow(new BusinessForbiddenException("DENIED", "无权读取"));
        assertThrows(BusinessForbiddenException.class, () -> service.executeBoundOperation(request));
        verify(provider, times(1)).execute(any(), any(), anyMap(), anyMap(), any());
    }
}
