package com.workflow.admin.audit.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.audit.api.request.SystemAuditQuery;
import com.workflow.admin.audit.api.response.UnifiedAuditEventView;
import com.workflow.admin.audit.api.request.UnifiedAuditQuery;
import com.workflow.admin.audit.infrastructure.persistence.record.SystemOperationLog;
import com.workflow.admin.audit.infrastructure.persistence.mapper.SystemOperationLogMapper;
import com.workflow.core.result.PageResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SystemAuditQueryServiceUnifiedTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SystemOperationLog.class);
    }

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
                new SystemAuditQueryService(mapper, com.workflow.integration.database.api.query.DatabaseQueryDialects.forDatabaseId("MYSQL"));

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
                new SystemAuditQueryService(mapper, com.workflow.integration.database.api.query.DatabaseQueryDialects.forDatabaseId("MYSQL"));

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
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<SystemOperationLog> page = invocation.getArgument(0);
            LambdaQueryWrapper<SystemOperationLog> query = invocation.getArgument(1);
            assertEquals(1, page.getCurrent());
            assertEquals(500, page.getSize());
            assertFalse(page.searchCount());
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("operation_id IS NULL"));
            assertTrue(sql.contains("event_id ="));
            assertTrue(sql.contains("ORDER BY create_time ASC,id ASC"));
            assertTrue(query.getParamNameValuePairs().containsValue("event-1"));
            assertTrue(query.getParamNameValuePairs().containsValue(""));
            page.setRecords(List.of(legacy));
            return page;
        });
        SystemAuditQueryService service =
                new SystemAuditQueryService(mapper, com.workflow.integration.database.api.query.DatabaseQueryDialects.forDatabaseId("MYSQL"));

        List<UnifiedAuditEventView> result =
                service.operationTimeline("event-1");

        assertEquals("event-1", result.get(0).operationId());
    }

    @Test
    void exportKeepsFullRecordsAndTenThousandRowLimitWithoutCount() {
        SystemOperationLogMapper mapper = mock(SystemOperationLogMapper.class);
        SystemOperationLog value = log();
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<SystemOperationLog> page = invocation.getArgument(0);
            LambdaQueryWrapper<SystemOperationLog> query = invocation.getArgument(1);
            assertEquals(1, page.getCurrent());
            assertEquals(10000, page.getSize());
            assertFalse(page.searchCount());
            assertNull(query.getSqlSelect());
            assertTrue(query.getSqlSegment().contains("ORDER BY create_time DESC,id DESC"));
            assertTrue(query.getParamNameValuePairs().containsValue("ENTITY"));
            page.setRecords(List.of(value));
            return page;
        });
        var service = new SystemAuditQueryService(mapper,
                com.workflow.integration.database.api.query.DatabaseQueryDialects.forDatabaseId("MYSQL"));
        var query = new SystemAuditQuery();
        query.setModule("entity");

        assertEquals(List.of(value), service.export(query));
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
