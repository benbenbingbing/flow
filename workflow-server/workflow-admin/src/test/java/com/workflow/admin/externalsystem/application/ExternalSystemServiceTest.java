package com.workflow.admin.externalsystem.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.externalsystem.api.ExternalSystemErrorCode;
import com.workflow.admin.externalsystem.api.ExternalSystemManagementException;
import com.workflow.admin.externalsystem.api.request.ExternalSystemRequests;
import com.workflow.admin.externalsystem.infrastructure.persistence.mapper.ExternalSystemMapper;
import com.workflow.admin.externalsystem.infrastructure.persistence.mapper.ExternalSystemParameterMapper;
import com.workflow.admin.externalsystem.infrastructure.persistence.record.ExternalSystemParameterRecord;
import com.workflow.admin.externalsystem.infrastructure.persistence.record.ExternalSystemRecord;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.SystemAudit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 外部系统聚合保存、永久编码和参数唯一性核心规则测试。
 */
class ExternalSystemServiceTest {

    private final ExternalSystemMapper externalSystemMapper =
            mock(ExternalSystemMapper.class);
    private final ExternalSystemParameterMapper parameterMapper =
            mock(ExternalSystemParameterMapper.class);
    private final ExternalSystemService service = new ExternalSystemService(
            externalSystemMapper, parameterMapper);

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("actor-1", "operator");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createAtomicallyPersistsParentAndParametersWithoutTrimmingValues() {
        when(externalSystemMapper.selectAnyByCode("erp-main"))
                .thenReturn(null);
        doAnswer(invocation -> {
            ExternalSystemRecord record = invocation.getArgument(0);
            record.setId("external-1");
            return 1;
        }).when(externalSystemMapper).insert(any(ExternalSystemRecord.class));
        AtomicInteger parameterSequence = new AtomicInteger();
        doAnswer(invocation -> {
            ExternalSystemParameterRecord record = invocation.getArgument(0);
            record.setId("parameter-" + parameterSequence.incrementAndGet());
            return 1;
        }).when(parameterMapper).insert(
                any(ExternalSystemParameterRecord.class));

        var result = service.create(
                new ExternalSystemRequests.CreateExternalSystem(
                        " erp-main ",
                        " ERP 主系统 ",
                        null,
                        " https://erp.example.com ",
                        " 核心业务系统 ",
                        List.of(
                                new ExternalSystemRequests.ParameterInput(
                                        null,
                                        " 请求模板 ",
                                        "requestTemplate",
                                        "  ${request-template}  ",
                                        null),
                                new ExternalSystemRequests.ParameterInput(
                                        null,
                                        "租户编码",
                                        "tenantCode",
                                        "tenant-a",
                                        3))));

        assertEquals("external-1", result.id());
        assertEquals("external-1", result.getId());
        assertEquals("erp-main", result.systemCode());
        assertEquals("ERP 主系统", result.systemName());
        assertEquals("0", result.status());
        assertEquals("https://erp.example.com", result.address());
        assertEquals(0L, result.version());
        assertEquals(2, result.parameters().size());
        assertEquals("  ${request-template}  ",
                result.parameters().get(0).value());
        assertEquals(10, result.parameters().get(0).sortOrder());
        assertEquals(3, result.parameters().get(1).sortOrder());

        ArgumentCaptor<ExternalSystemRecord> parentCaptor =
                ArgumentCaptor.forClass(ExternalSystemRecord.class);
        verify(externalSystemMapper).insert(parentCaptor.capture());
        assertEquals("actor-1", parentCaptor.getValue().getCreatedBy());
        assertEquals("actor-1", parentCaptor.getValue().getUpdatedBy());

        ArgumentCaptor<ExternalSystemParameterRecord> parameterCaptor =
                ArgumentCaptor.forClass(ExternalSystemParameterRecord.class);
        verify(parameterMapper,
                org.mockito.Mockito.times(2)).insert(
                        parameterCaptor.capture());
        assertTrue(parameterCaptor.getAllValues().stream().allMatch(
                item -> "external-1".equals(item.getExternalSystemId())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageClampsPageNumberAndSizeToSupportedBounds() {
        when(externalSystemMapper.selectPage(
                any(Page.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.page(
                0, 1000, null, null, null);

        assertEquals(1, result.getPageNum());
        assertEquals(100, result.getPageSize());
        assertTrue(result.getRecords().isEmpty());
        verify(parameterMapper, never())
                .countActiveByExternalSystemIds(any());
    }

    @Test
    void deletedSystemCodeStillCannotBeReused() {
        ExternalSystemRecord deleted = externalSystem("deleted-1", "erp-main");
        deleted.setDeleted(1);
        when(externalSystemMapper.selectAnyByCode("erp-main"))
                .thenReturn(deleted);

        ExternalSystemManagementException exception = assertThrows(
                ExternalSystemManagementException.class,
                () -> service.create(createRequest(
                        "erp-main", List.of())));

        assertEquals(409, exception.status());
        assertEquals(
                ExternalSystemErrorCode.EXTERNAL_SYSTEM_CODE_DUPLICATED,
                exception.errorCode());
        verify(externalSystemMapper, never()).insert(
                any(ExternalSystemRecord.class));
    }

    @Test
    void parameterEnglishNamesAreUniqueIgnoringCase() {
        ExternalSystemManagementException exception = assertThrows(
                ExternalSystemManagementException.class,
                () -> service.create(createRequest(
                        "erp-main",
                        List.of(
                                parameter("客户端标识", "clientId", "one"),
                                parameter("备用标识", "CLIENTID", "two")))));

        assertEquals(409, exception.status());
        assertEquals(
                ExternalSystemErrorCode
                        .EXTERNAL_SYSTEM_PARAMETER_NAME_DUPLICATED,
                exception.errorCode());
        verify(externalSystemMapper, never()).selectAnyByCode(anyString());
        verify(externalSystemMapper, never()).insert(
                any(ExternalSystemRecord.class));
    }

    @Test
    void parameterNamesAllowDotAndHyphenAndValuesUseLongTextLimit() {
        when(externalSystemMapper.selectAnyByCode("erp-main"))
                .thenReturn(null);
        doAnswer(invocation -> {
            ExternalSystemRecord record = invocation.getArgument(0);
            record.setId("external-1");
            return 1;
        }).when(externalSystemMapper).insert(any(ExternalSystemRecord.class));
        String longValue = "v".repeat(65535);

        var result = service.create(createRequest(
                "erp-main",
                List.of(parameter(
                        "签名模板", "signature.key-name", longValue))));

        assertEquals("signature.key-name",
                result.parameters().get(0).nameEn());
        assertEquals(longValue, result.parameters().get(0).value());
    }

    @Test
    void parameterUniqueConstraintFailureUsesStableConflictCode() {
        when(externalSystemMapper.selectAnyByCode("erp-main"))
                .thenReturn(null);
        doAnswer(invocation -> {
            ExternalSystemRecord record = invocation.getArgument(0);
            record.setId("external-1");
            return 1;
        }).when(externalSystemMapper).insert(any(ExternalSystemRecord.class));
        when(parameterMapper.insert(any(ExternalSystemParameterRecord.class)))
                .thenThrow(new DuplicateKeyException("constraint"));

        ExternalSystemManagementException exception = assertThrows(
                ExternalSystemManagementException.class,
                () -> service.create(createRequest(
                        "erp-main",
                        List.of(parameter("客户端标识", "clientId", "value")))));

        assertEquals(409, exception.status());
        assertEquals(
                ExternalSystemErrorCode
                        .EXTERNAL_SYSTEM_PARAMETER_NAME_DUPLICATED,
                exception.errorCode());
    }

    @Test
    void updateLocksParentKeepsCodeAndRebuildsParameters() {
        ExternalSystemRecord current = externalSystem(
                "external-1", "immutable-code");
        when(externalSystemMapper.selectForUpdate("external-1"))
                .thenReturn(current);
        when(externalSystemMapper.updateMutableFields(
                anyString(), anyString(), anyString(), anyString(),
                any(), anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);
        when(parameterMapper.softDeleteByExternalSystemId(
                anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(2);
        doAnswer(invocation -> {
            ExternalSystemParameterRecord record = invocation.getArgument(0);
            record.setId("new-parameter");
            return 1;
        }).when(parameterMapper).insert(
                any(ExternalSystemParameterRecord.class));

        var result = service.update(
                "external-1",
                new ExternalSystemRequests.UpdateExternalSystem(
                        "新名称",
                        "1",
                        "https://new.example.com",
                        null,
                        List.of(new ExternalSystemRequests.ParameterInput(
                                "old-parameter",
                                "应用键",
                                "appKey",
                                "preserve-this-value",
                                5)),
                        0L));

        assertEquals("immutable-code", result.systemCode());
        assertEquals(1L, result.version());
        assertEquals("new-parameter", result.parameters().get(0).id());
        assertNotEquals("old-parameter", result.parameters().get(0).id());

        InOrder order = inOrder(externalSystemMapper, parameterMapper);
        order.verify(externalSystemMapper).selectForUpdate("external-1");
        order.verify(externalSystemMapper).updateMutableFields(
                anyString(), anyString(), anyString(), anyString(),
                any(), eq(0L), anyString(), any(LocalDateTime.class));
        order.verify(parameterMapper).softDeleteByExternalSystemId(
                anyString(), anyString(), any(LocalDateTime.class));
        order.verify(parameterMapper).insert(
                any(ExternalSystemParameterRecord.class));
        verify(externalSystemMapper, never()).selectAnyByCode(anyString());
    }

    @Test
    void deleteLocksParentAndSoftDeletesChildrenBeforeParent() {
        when(externalSystemMapper.selectForUpdate("external-1"))
                .thenReturn(externalSystem("external-1", "erp-main"));
        when(parameterMapper.softDeleteByExternalSystemId(
                anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(2);
        when(externalSystemMapper.softDelete(
                anyString(), anyLong(), anyString(),
                any(LocalDateTime.class)))
                .thenReturn(1);

        service.delete(
                "external-1",
                new ExternalSystemRequests.DeleteExternalSystem(0L));

        InOrder order = inOrder(externalSystemMapper, parameterMapper);
        order.verify(externalSystemMapper).selectForUpdate("external-1");
        order.verify(parameterMapper).softDeleteByExternalSystemId(
                eq("external-1"), eq("actor-1"), any(LocalDateTime.class));
        order.verify(externalSystemMapper).softDelete(
                eq("external-1"), eq(0L), eq("actor-1"),
                any(LocalDateTime.class));
    }

    @Test
    void changeStatusLocksParentBeforeWriting() {
        when(externalSystemMapper.selectForUpdate("external-1"))
                .thenReturn(externalSystem("external-1", "erp-main"));
        when(externalSystemMapper.updateStatus(
                anyString(), anyString(), anyLong(), anyString(),
                any(LocalDateTime.class)))
                .thenReturn(1);

        service.changeStatus(
                "external-1",
                new ExternalSystemRequests.ChangeExternalSystemStatus(
                        "1", 0L));

        InOrder order = inOrder(externalSystemMapper);
        order.verify(externalSystemMapper).selectForUpdate("external-1");
        order.verify(externalSystemMapper).updateStatus(
                eq("external-1"), eq("1"), eq(0L), eq("actor-1"),
                any(LocalDateTime.class));
    }

    @Test
    void staleExpectedVersionFailsBeforeAnyAggregateWrite() {
        ExternalSystemRecord current = externalSystem(
                "external-1", "immutable-code");
        current.setVersion(3L);
        when(externalSystemMapper.selectForUpdate("external-1"))
                .thenReturn(current);

        ExternalSystemManagementException exception = assertThrows(
                ExternalSystemManagementException.class,
                () -> service.update(
                        "external-1",
                        new ExternalSystemRequests.UpdateExternalSystem(
                                "新名称",
                                "0",
                                "https://new.example.com",
                                null,
                                List.of(),
                                2L)));

        assertEquals(409, exception.status());
        assertEquals(
                ExternalSystemErrorCode.EXTERNAL_SYSTEM_VERSION_CONFLICT,
                exception.errorCode());
        verify(externalSystemMapper, never()).updateMutableFields(
                anyString(), anyString(), anyString(), anyString(),
                any(), anyLong(), anyString(), any(LocalDateTime.class));
        verify(parameterMapper, never()).softDeleteByExternalSystemId(
                anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void auditAnnotationsAreRequiredAndNeverCaptureParameterValues()
            throws Exception {
        List<Method> auditedMethods = List.of(
                ExternalSystemService.class.getDeclaredMethod(
                        "create",
                        ExternalSystemRequests.CreateExternalSystem.class),
                ExternalSystemService.class.getDeclaredMethod(
                        "update",
                        String.class,
                        ExternalSystemRequests.UpdateExternalSystem.class),
                ExternalSystemService.class.getDeclaredMethod(
                        "changeStatus",
                        String.class,
                        ExternalSystemRequests
                                .ChangeExternalSystemStatus.class),
                ExternalSystemService.class.getDeclaredMethod(
                        "delete", String.class,
                        ExternalSystemRequests.DeleteExternalSystem.class));

        for (Method method : auditedMethods) {
            SystemAudit audit = method.getAnnotation(SystemAudit.class);
            assertTrue(audit.required(), method.getName());
            assertFalse(audit.captureArguments(), method.getName());
            assertFalse(audit.captureResult(), method.getName());
        }
    }

    @Test
    void compoundReadsUseReadOnlyTransactionsForConsistentSnapshots()
            throws Exception {
        Method page = ExternalSystemService.class.getDeclaredMethod(
                "page", int.class, int.class, String.class,
                String.class, String.class);
        Method get = ExternalSystemService.class.getDeclaredMethod(
                "get", String.class);

        assertTrue(page.getAnnotation(Transactional.class).readOnly());
        assertTrue(get.getAnnotation(Transactional.class).readOnly());
    }

    private ExternalSystemRequests.CreateExternalSystem createRequest(
            String code,
            List<ExternalSystemRequests.ParameterInput> parameters) {
        return new ExternalSystemRequests.CreateExternalSystem(
                code,
                "ERP 系统",
                "0",
                "https://erp.example.com",
                null,
                parameters);
    }

    private ExternalSystemRequests.ParameterInput parameter(
            String nameZh,
            String nameEn,
            String value) {
        return new ExternalSystemRequests.ParameterInput(
                null, nameZh, nameEn, value, null);
    }

    private ExternalSystemRecord externalSystem(String id, String code) {
        ExternalSystemRecord record = new ExternalSystemRecord();
        record.setId(id);
        record.setSystemCode(code);
        record.setSystemName("ERP 系统");
        record.setStatus("0");
        record.setAddress("https://erp.example.com");
        record.setVersion(0L);
        record.setCreatedBy("creator-1");
        record.setUpdatedBy("creator-1");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(0);
        return record;
    }
}
