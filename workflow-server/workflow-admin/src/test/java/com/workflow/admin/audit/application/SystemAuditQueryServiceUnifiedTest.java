package com.workflow.admin.audit.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.audit.api.UnifiedAuditEventView;
import com.workflow.admin.audit.api.UnifiedAuditQuery;
import com.workflow.admin.audit.domain.SystemOperationLog;
import com.workflow.admin.audit.infrastructure.SystemOperationLogMapper;
import com.workflow.core.result.PageResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SystemAuditQueryServiceUnifiedTest {

    @Test
    void unifiedPageReturnsOnlyNarrowedProjectionAndSourcePointer() {
        SystemOperationLogMapper mapper =
                mock(SystemOperationLogMapper.class);
        Page<SystemOperationLog> page = new Page<>(1, 20);
        page.setRecords(List.of(log()));
        page.setTotal(1);
        when(mapper.selectPage(any(Page.class), any()))
                .thenReturn(page);
        SystemAuditQueryService service =
                new SystemAuditQueryService(mapper);

        PageResult<UnifiedAuditEventView> result =
                service.unifiedPage(new UnifiedAuditQuery());

        UnifiedAuditEventView event = result.getRecords().get(0);
        assertEquals("operation-1", event.operationId());
        assertEquals("FORM", event.source().type());
        assertEquals("form-1", event.source().id());
        assertTrue(event.payloadDetailsOmitted());
        // 用字段白名单锁住统一接口的最小投影，后续若误加原始载荷、网络信息
        // 或错误详情，测试会直接失败，而不是只依赖调用方记得不读取。
        assertEquals(List.of(
                        "id", "eventId", "operationId",
                        "parentOperationId", "traceId", "module",
                        "operationCode", "operationName", "result",
                        "riskLevel", "operatorId", "operatorName",
                        "targetType", "targetId", "targetName", "summary",
                        "errorCode", "durationMs", "source",
                        "payloadDetailsOmitted", "createdAt"),
                java.util.Arrays.stream(
                                UnifiedAuditEventView.class
                                        .getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList());
        assertThrows(NoSuchMethodException.class,
                () -> UnifiedAuditEventView.class.getMethod("beforeJson"));
        assertThrows(NoSuchMethodException.class,
                () -> UnifiedAuditEventView.class.getMethod("operatorIp"));
    }

    @Test
    void operationTimelineRejectsOversizedIdentifierBeforeDatabase() {
        SystemOperationLogMapper mapper =
                mock(SystemOperationLogMapper.class);
        SystemAuditQueryService service =
                new SystemAuditQueryService(mapper);

        assertThrows(IllegalArgumentException.class,
                () -> service.operationTimeline("x".repeat(129)));

        verifyNoInteractions(mapper);
    }

    @Test
    void legacyNullOperationIsExposedAndQueriedAsItsOwnEvent() {
        SystemOperationLogMapper mapper =
                mock(SystemOperationLogMapper.class);
        SystemOperationLog legacy = log();
        legacy.setOperationId(null);
        when(mapper.selectList(any())).thenReturn(List.of(legacy));
        SystemAuditQueryService service =
                new SystemAuditQueryService(mapper);

        List<UnifiedAuditEventView> result =
                service.operationTimeline("event-1");

        assertEquals("event-1", result.get(0).operationId());
    }

    private SystemOperationLog log() {
        SystemOperationLog value = new SystemOperationLog();
        value.setId("log-1");
        value.setEventId("event-1");
        value.setOperationId("operation-1");
        value.setTraceId("trace-1");
        value.setModuleCode("ENTITY");
        value.setOperationCode("UPDATE");
        value.setOperationName("更新实体");
        value.setResult("SUCCESS");
        value.setRiskLevel("MEDIUM");
        value.setOperatorId("user-1");
        value.setOperatorName("张三");
        value.setTargetType("ENTITY_RECORD");
        value.setTargetId("asset:1");
        value.setSummary("实体变更已提交");
        value.setBeforeJson("{\"password\":\"should-not-leak\"}");
        value.setOperatorIp("127.0.0.1");
        value.setUserAgent("secret-agent");
        value.setSourceSystem("ENTITY_MUTATION");
        value.setSourceType("FORM");
        value.setSourceId("form-1");
        value.setSourceEventId("mutation-1");
        value.setCreateTime(LocalDateTime.now());
        return value;
    }
}
