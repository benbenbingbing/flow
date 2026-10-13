package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.contracts.entity.ui.context.UiInvocationContext;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.*;
import com.workflow.entity.excel.runtime.ExcelAtomicExecutionContext;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.api.request.*;
import com.workflow.entity.ui.application.model.UiDataSourceExecutionAuthorization;
import com.workflow.entity.ui.application.validation.UiExtensionDefinitionValidator;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UiInterfaceExcelExecutionTest {
    @Test void currentWriteDefinitionIsBlockedEvenAfterEarlierReadCheck() {
        var service = service(List.of(), Runnable::run);
        var definition = definition("STATIC_OPTIONS");
        try (var ignored = ExcelAtomicExecutionContext.open()) {
            ExcelAtomicExecutionContext.requireRead(definition.getInterfaceKind(), Map.of());
            // 模拟资格检查后管理员把接口改成 WRITE；实际调用必须在任何 Provider 或缓存访问之前拒绝。
            definition.setInterfaceKind("WRITE");
            assertThatThrownBy(() -> execute(service, definition)).hasMessageContaining("仅支持READ");
        }
        assertThat(ExcelAtomicExecutionContext.active()).isFalse();
        assertThat((List<?>) execute(service, definition)).isEmpty();
    }

    @Test void actualFallbackPolicyCannotHideImportValidationFailure() {
        var service = service(List.of(), Runnable::run);
        var definition = definition("STATIC_OPTIONS");
        definition.setExecutionPolicyDocument("{\"failurePolicy\":\"EMPTY\"}");
        try (var ignored = ExcelAtomicExecutionContext.open()) {
            assertThatThrownBy(() -> execute(service, definition)).hasMessageContaining("失败即终止");
        }
    }

    @Test void poolCarriesTrustedOriginAndReadRestrictionThenCleansBothOnFailure() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicInteger calls = new AtomicInteger();
        var request = new UiEventExecuteRequest(); request.setConfigId("form");
        var chain = new UiEventBindingService.ResolvedEventChain(List.of(), "hash", 1, "entity", "employee", null, Map.of());
        UiDataSourceProvider provider = new UiDataSourceProvider() {
            public String getCode() { return "test"; }
            public String getDisplayName() { return "test"; }
            public String getArtifactDigest() { return "a".repeat(64); }
            public Object execute(UiInvocationContext context, DataScopePlan scope, Map<String, Object> config, Map<String, Object> input) {
                calls.incrementAndGet();
                assertThat(UiEventOriginContext.current().request()).isSameAs(request);
                assertThat(ExcelAtomicExecutionContext.active()).isTrue();
                assertThat(UserContext.getUserId()).isEqualTo("alice");
                // 下游调用也继承限制，不能通过一个 READ Provider 转调 WRITE 接口。
                ExcelAtomicExecutionContext.requireRead("WRITE", Map.of());
                return Map.of();
            }
        };
        try {
            var service = service(List.of(provider), pool::execute);
            try (var origin = UiEventOriginContext.open(request, chain); var atomic = ExcelAtomicExecutionContext.open()) {
                assertThatThrownBy(() -> execute(service, definition("REGISTERED_PROVIDER"))).hasMessageContaining("仅支持READ");
                assertThat(UiEventOriginContext.current().request()).isSameAs(request);
            }
            assertThat(calls.get()).isEqualTo(1);
            pool.submit(() -> {
                assertThat(UiEventOriginContext.current()).isNull();
                assertThat(ExcelAtomicExecutionContext.active()).isFalse();
                assertThat(UserContext.getUserId()).isNull();
            }).get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    private Object execute(UiInterfaceExtensionService service, UiExtensionDefinition definition) {
        var user = new SysUser(); user.setId("alice"); user.setUsername("alice");
        var authorization = new UiDataSourceExecutionAuthorization(false, "FORM", "form", "hash", 1, "binding", "DATA_CREATE", "entity", "employee", null,
                user, new DataScopePlan(true, "1=1", Map.of(), List.of(), List.of(), "allowed", 1), Map.of(), "row");
        var request = new UiExtensionExecuteRequest(); request.setInput(Map.of());
        // 此测试从已授权边界进入，专门验证与 Provider 共用内存定义的执行检查及真实池线程传播。
        return ReflectionTestUtils.invokeMethod(service, "executeAuthorized", definition, request, authorization);
    }

    private UiExtensionDefinition definition(String implementationType) {
        var value = new UiExtensionDefinition(); value.setId("interface"); value.setInterfaceKind("READ");
        value.setImplementationType(implementationType); value.setProviderCode("test"); value.setImplementationConfigDocument("{}");
        value.setExecutionPolicyDocument("{}"); value.setInputSchemaDocument("{}"); value.setOutputSchemaDocument("{}"); return value;
    }

    private UiInterfaceExtensionService service(List<UiDataSourceProvider> providers, TaskExecutor executor) {
        return new UiInterfaceExtensionService(mock(UiExtensionDefinitionMapper.class), mock(EntityFormMapper.class), mock(EntityListConfigMapper.class),
                mock(EntityDefinitionAccessPolicy.class), mock(EntityUiConfigurationPolicy.class), mock(SysDictItemService.class),
                mock(UiDataSourceExecutionAccessService.class), mock(UiInvocationContextFactory.class), mock(UiExtensionDefinitionValidator.class),
                providers, new JsonDocumentCodec(new ObjectMapper()), executor);
    }
}
