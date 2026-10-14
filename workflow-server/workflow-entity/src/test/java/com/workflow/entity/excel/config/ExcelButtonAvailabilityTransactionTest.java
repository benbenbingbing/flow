package com.workflow.entity.excel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository.SchemeRow;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 使用真实配置解析器、Spring 事务代理和 H2 提交验证展示探测不会污染调用方事务。
 * 单纯 mock 解析器抛异常无法复现 REQUIRED 参与事务后留下 rollback-only 的问题。
 */
class ExcelButtonAvailabilityTransactionTest {
    private final ExcelConfigurationRepository repository = mock(ExcelConfigurationRepository.class);
    private final UiEventBindingService bindings = mock(UiEventBindingService.class);
    private final ObjectMapper json = new ObjectMapper();
    private EntityExcelConfigurationService configurations;
    private ExcelButtonAvailabilityService availability;
    private ExcelButtonAvailabilityService unisolatedAvailability;
    private TransactionTemplate outerTransaction;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:excel_availability_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE transaction_markers (id VARCHAR(100) PRIMARY KEY)");
        var manager = new DataSourceTransactionManager(source);
        outerTransaction = new TransactionTemplate(manager);

        var entity = new EntityDefinition();
        entity.setId("entity1");
        entity.setEntityCode("employee");
        var policy = mock(EntityUiConfigurationPolicy.class);
        when(policy.requireConfigurableByCode("employee")).thenReturn(entity);
        var configurationTarget = new EntityExcelConfigurationService(
                repository,
                mock(EntityDefinitionMapper.class),
                mock(EntityPublishedSnapshotService.class),
                mock(EntityFormMapper.class),
                mock(EntityFormPublishedMapper.class),
                mock(CurrentUserRoleService.class),
                policy,
                new SystemEntityFieldPolicy(),
                new ExcelSchemeValidator(org.mockito.Mockito.mock(com.workflow.entity.excel.config.EntityExcelReferenceMetadata.class)),
                json);
        configurations = transactionalProxy(configurationTarget, manager);

        unisolatedAvailability = new ExcelButtonAvailabilityService(
                configurations, provider(bindings), provider(mock(UiInterfaceExtensionService.class)), json);
        availability = transactionalProxy(unisolatedAvailability, manager);
    }

    @Test
    void missingDefaultSchemesDoNotRollBackOuterTransaction() {
        for (String operation : List.of("IMPORT", "EXPORT")) {
            String direction = "IMPORT".equals(operation) ? "导入" : "导出";
            assertOuterTransactionCommits("default-" + operation, () ->
                    assertThat(availability.defaultReason("employee", operation))
                            .isEqualTo("请先配置默认" + direction + "方案"));
        }
    }

    @Test
    void invalidExplicitListSchemeDoesNotRollBackToolbarOrRowDisplay() {
        configureExcelEvent("EXPLICIT", "missing");
        var list = new EntityListConfig();
        list.setId("list1");
        list.setListKey("employees");
        list.setPublishedSnapshot(true);
        var buttons = List.<Map<String, Object>>of(Map.of("key", "excel", "type", "custom"));

        for (String event : List.of("TOOLBAR_BUTTON_CLICK", "ROW_BUTTON_CLICK")) {
            assertOuterTransactionCommits(event, () ->
                    assertThat(availability.listReasons("employee", list, buttons, event))
                            .containsEntry("excel", "Excel 方案不存在: missing"));
        }
        // EXPLICIT 失效只禁用这个按钮，不能通过回退默认方案改变其操作含义。
        verify(repository, never()).settings(any());
    }

    @Test
    void disabledFormSchemeDoesNotRollBackEitherFormDisplayOverload() {
        configureExcelEvent("EXPLICIT", "disabled");
        var disabled = new SchemeRow();
        disabled.setStatus("DISABLED");
        when(repository.scheme("entity1", "disabled")).thenReturn(disabled);
        var form = new EntityForm();
        form.setId("form1");
        var button = Map.<String, Object>of("key", "excel", "customMode", "event");

        // 三参数入口内部调用四参数重载，也必须先经过外层代理暂停调用方事务。
        assertOuterTransactionCommits("form-without-record", () ->
                assertThat(availability.formReason("employee", form, button))
                        .isEqualTo("Excel 方案未发布或已停用: disabled"));
        assertOuterTransactionCommits("form-with-record", () ->
                assertThat(availability.formReason("employee", form, button, "record1"))
                        .isEqualTo("Excel 方案未发布或已停用: disabled"));
    }

    @Test
    void unisolatedDisplayProbeReproducesRollbackOnlyDespiteCatchingFailure() {
        configureExcelEvent("DEFAULT", null);
        var form = new EntityForm();
        form.setId("form1");

        // 绕过展示服务代理复现修复前调用链；配置服务仍经过真实 REQUIRED 代理。
        assertThatThrownBy(() -> outerTransaction.executeWithoutResult(status -> {
            insertMarker("unisolated");
            assertThat(unisolatedAvailability.formReason("employee", form, Map.of("key", "excel")))
                    .isEqualTo("请先配置默认导出方案");
            assertThat(status.isRollbackOnly()).isTrue();
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(markerCount()).isZero();
    }

    @Test
    void strictConfigurationResolutionStillMarksBusinessTransactionForRollback() {
        // 真正的执行和发布校验直接调用严格解析器，不能因展示容错而失去整体回滚语义。
        assertThatThrownBy(() -> outerTransaction.executeWithoutResult(status -> {
            insertMarker("strict-before");
            assertThatThrownBy(() -> configurations.resolvePublished("employee", "EXPORT", "DEFAULT", null))
                    .isInstanceOf(BusinessConflictException.class)
                    .hasMessage("请先配置默认导出方案");
            assertThat(status.isRollbackOnly()).isTrue();
            insertMarker("strict-after");
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(markerCount()).isZero();
    }

    /**
     * 在探测前后写入同一调用方事务，验证失败提示返回后该事务已恢复且可以整体提交。
     * 检查真实落库结果，避免仅断言方法返回值而漏掉提交时才抛出的回滚异常。
     */
    private void assertOuterTransactionCommits(String marker, Runnable probeAssertions) {
        outerTransaction.executeWithoutResult(status -> {
            insertMarker(marker + "-before");
            probeAssertions.run();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(status.isRollbackOnly()).isFalse();
            insertMarker(marker + "-after");
        });
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transaction_markers WHERE id IN (?, ?)", Integer.class,
                marker + "-before", marker + "-after")).isEqualTo(2);
    }

    /** 事件链只提供冻结主步骤，回滚必须由生产配置解析器的事务代理触发。 */
    private void configureExcelEvent(String mode, String schemeCode) {
        Map<String, Object> reference = "DEFAULT".equals(mode)
                ? Map.of("mode", mode)
                : Map.of("mode", mode, "schemeCode", schemeCode);
        var document = Map.of(
                "providerCode", "entity_excel_action",
                "implementationConfigDocument", Map.of("operation", "EXPORT", "schemeRef", reference));
        var chain = new UiEventBindingService.ResolvedEventChain(
                List.of(Map.of("strategy", "REPLACE", "executableSnapshot", document)),
                "release1", 1, "entity1", "employee", null, Map.of());
        when(bindings.resolvePublished(any())).thenReturn(chain);
    }

    private void insertMarker(String marker) {
        jdbc.update("INSERT INTO transaction_markers (id) VALUES (?)", marker);
    }

    private int markerCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transaction_markers", Integer.class);
    }

    /** 配置和展示服务都读取生产注解，确保删掉隔离注解时这些回归用例会真实失败。 */
    @SuppressWarnings("unchecked")
    private <T> T transactionalProxy(T target, DataSourceTransactionManager manager) {
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(value);
        return provider;
    }
}
