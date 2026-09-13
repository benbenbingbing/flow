package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.application.UiEventExecutionReceiptService;
import com.workflow.entity.version.application.EntityMutationReceiptService;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityMutationReceiptMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityMutationReceipt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用真实 H2 连接和 Spring 事务代理验证表单按钮幂等占位的提交边界。
 * Mapper 仅作为薄 JDBC 适配器，PENDING/SUCCESS 状态均实际写入数据库，
 * 因而回调异常后的断言能够识别“永久 IN_PROGRESS”回归。
 */
class UiEventExecutionReceiptTransactionIntegrationTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private UiEventExecutionReceiptService service;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:ui_event_receipt_" + UUID.randomUUID()
                        + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                "sa",
                "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE entity_mutation_receipt (
                  id VARCHAR(64) PRIMARY KEY,
                  idempotency_key VARCHAR(255) NOT NULL UNIQUE,
                  command_hash VARCHAR(64) NOT NULL,
                  operation_id VARCHAR(128),
                  entity_code VARCHAR(128),
                  record_id VARCHAR(128),
                  operation_type VARCHAR(32),
                  status VARCHAR(16) NOT NULL,
                  result_document CLOB,
                  version_no INT,
                  version_scenario_code VARCHAR(128),
                  changed BOOLEAN,
                  create_time TIMESTAMP,
                  update_time TIMESTAMP
                )
                """);
        EntityMutationReceiptMapper mapper = jdbcReceiptMapper();
        EntityMutationReceiptService receiptService =
                new EntityMutationReceiptService(
                        mapper, new ObjectMapper());
        SysUserService userService = mock(SysUserService.class);
        SysUser user = new SysUser();
        user.setId("user-a");
        user.setOrgId("tenant-a");
        when(userService.getById("user-a")).thenReturn(user);
        UiEventExecutionReceiptService target =
                new UiEventExecutionReceiptService(
                        receiptService,
                        userService,
                        new ObjectMapper());

        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(dataSource);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(
                new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(interceptor);
        service = (UiEventExecutionReceiptService) proxyFactory.getProxy();
        UserContext.setCurrentUser("user-a", "Alice");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void callbackFailureRollsBackPendingSoSameRequestCanAcquireAgain() {
        UiEventExecuteRequest first = request("request-rollback");

        assertThrows(InjectedProviderFailure.class,
                () -> service.execute(first, chain(), () -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    assertEquals(1, jdbc.queryForObject(
                            "SELECT COUNT(*) FROM entity_mutation_receipt",
                            Integer.class));
                    throw new InjectedProviderFailure();
                }));

        assertFalse(TransactionSynchronizationManager
                .isActualTransactionActive());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM entity_mutation_receipt",
                Integer.class));

        UiEventExecutionResult completed = service.execute(
                request("request-rollback"),
                chain(),
                () -> {
                    UiEventExecutionResult result =
                            new UiEventExecutionResult();
                    result.setData(Map.of("status", "DONE"));
                    return result;
                });

        assertEquals(Map.of("status", "DONE"), completed.getData());
        assertFalse(completed.isReplayed());
        assertEquals("SUCCESS", jdbc.queryForObject(
                "SELECT status FROM entity_mutation_receipt",
                String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM entity_mutation_receipt",
                Integer.class));
    }

    /**
     * 使用与生产 Mapper 等价的 SQL，并让 JdbcTemplate 自动加入线程绑定事务。
     */
    private EntityMutationReceiptMapper jdbcReceiptMapper() {
        EntityMutationReceiptMapper mapper =
                mock(EntityMutationReceiptMapper.class);
        when(mapper.findByIdempotencyKey(anyString()))
                .thenAnswer(invocation -> findReceipt(
                        invocation.getArgument(0), false));
        when(mapper.findByIdempotencyKeyForUpdate(anyString()))
                .thenAnswer(invocation -> findReceipt(
                        invocation.getArgument(0), true));
        when(mapper.insert(any(EntityMutationReceipt.class)))
                .thenAnswer(invocation -> {
                    EntityMutationReceipt receipt =
                            invocation.getArgument(0);
                    return jdbc.update("""
                                    INSERT INTO entity_mutation_receipt(
                                      id, idempotency_key, command_hash,
                                      operation_id, entity_code, record_id,
                                      operation_type, status, create_time,
                                      update_time)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                            receipt.getId(),
                            receipt.getIdempotencyKey(),
                            receipt.getCommandHash(),
                            receipt.getOperationId(),
                            receipt.getEntityCode(),
                            receipt.getRecordId(),
                            receipt.getOperationType(),
                            receipt.getStatus(),
                            receipt.getCreateTime(),
                            receipt.getUpdateTime());
                });
        when(mapper.complete(
                anyString(), anyString(), anyString(),
                any(), any(), any()))
                .thenAnswer(invocation -> jdbc.update("""
                                UPDATE entity_mutation_receipt
                                SET record_id = ?, status = 'SUCCESS',
                                    result_document = ?, version_no = ?,
                                    version_scenario_code = ?, changed = ?,
                                    update_time = CURRENT_TIMESTAMP
                                WHERE idempotency_key = ?
                                  AND status = 'PENDING'
                                """,
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(0)));
        return mapper;
    }

    private EntityMutationReceipt findReceipt(
            String idempotencyKey,
            boolean forUpdate) {
        String sql = """
                SELECT * FROM entity_mutation_receipt
                WHERE idempotency_key = ?
                """ + (forUpdate ? " FOR UPDATE" : "");
        return jdbc.query(
                        sql,
                        (resultSet, rowNum) -> receipt(resultSet),
                        idempotencyKey)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private EntityMutationReceipt receipt(ResultSet resultSet)
            throws SQLException {
        EntityMutationReceipt receipt = new EntityMutationReceipt();
        receipt.setId(resultSet.getString("id"));
        receipt.setIdempotencyKey(resultSet.getString(
                "idempotency_key"));
        receipt.setCommandHash(resultSet.getString("command_hash"));
        receipt.setOperationId(resultSet.getString("operation_id"));
        receipt.setEntityCode(resultSet.getString("entity_code"));
        receipt.setRecordId(resultSet.getString("record_id"));
        receipt.setOperationType(resultSet.getString("operation_type"));
        receipt.setStatus(resultSet.getString("status"));
        receipt.setResultDocument(resultSet.getString("result_document"));
        receipt.setVersionNo((Integer) resultSet.getObject("version_no"));
        receipt.setVersionScenarioCode(resultSet.getString(
                "version_scenario_code"));
        receipt.setChanged((Boolean) resultSet.getObject("changed"));
        return receipt;
    }

    private UiEventExecuteRequest request(String requestId) {
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setEventCode("FORM_BUTTON_CLICK");
        request.setTargetType("BUTTON");
        request.setTargetKey("generate");
        request.setRecordId("record-1");
        request.setRequestId(requestId);
        request.setInput(Map.of("form", Map.of("amount", 10)));
        return request;
    }

    private UiEventBindingService.ResolvedEventChain chain() {
        Map<String, Object> snapshot = Map.of(
                "configType", "FORM",
                "form", Map.of(
                        "id", "form-1",
                        "entityId", "entity-1"));
        return new UiEventBindingService.ResolvedEventChain(
                List.of(),
                "release-1",
                3,
                "entity-1",
                "expense",
                null,
                snapshot,
                "release-1",
                "effective-hash-1");
    }

    private static final class InjectedProviderFailure
            extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
