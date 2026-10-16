package com.workflow.process.assignment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.process.assignment.infrastructure.persistence.mapper.AssigneeIncidentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证筛选后的计数、跨 500 条分页、分页边界和跨库参数顺序。 */
class AssigneeIncidentPaginationTest {
    private final AssigneeIncidentMapper mapper = mock(AssigneeIncidentMapper.class);

    private AssigneeIncidentService service(String database) {
        return new AssigneeIncidentService(mapper, new ObjectMapper(), null, null, null,
                DatabaseQueryDialects.forDatabaseId(database), null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MYSQL", "POSTGRESQL", "ORACLE"})
    void pagesBeyondOriginalLimitWithFilteredTotalAndBoundParameters(String database) {
        when(mapper.count(any())).thenReturn(1234L);
        var row = new LinkedCaseInsensitiveMap<Object>();
        row.put("ID", "incident-601");
        row.put("NODE_NAME", "审批节点");
        when(mapper.selectRows(any())).thenReturn(List.of(row));

        var page = service(database).list(" OPEN ", 31, 20);
        assertEquals(1234L, page.getTotal());
        assertEquals(31, page.getPageNum());
        assertEquals(20, page.getPageSize());
        assertEquals("incident-601", page.getRecords().get(0).get("id"));
        assertEquals("审批节点", page.getRecords().get(0).get("nodeName"));

        var count = ArgumentCaptor.forClass(ParameterizedSql.class);
        verify(mapper).count(count.capture());
        assertTrue(count.getValue().sql().endsWith(" WHERE status = ?"));
        assertEquals(List.of("OPEN"), values(count.getValue()));
        var query = ArgumentCaptor.forClass(ParameterizedSql.class);
        verify(mapper).selectRows(query.capture());
        assertTrue(query.getValue().sql().contains("WHERE status = ?"));
        assertTrue(query.getValue().sql().contains("update_time DESC, id DESC"));
        assertEquals(database.equals("POSTGRESQL") ? List.of("OPEN", 20L, 600L)
                : List.of("OPEN", 600L, 20L), values(query.getValue()));
    }

    @Test
    void blankStatusUsesUnfilteredCountAndDefaultPage() {
        when(mapper.count(any())).thenReturn(1L);
        when(mapper.selectRows(any())).thenReturn(List.of());
        var page = service("MYSQL").list("  ", null, null);
        assertEquals(1, page.getPageNum());
        assertEquals(20, page.getPageSize());
        var count = ArgumentCaptor.forClass(ParameterizedSql.class);
        verify(mapper).count(count.capture());
        assertFalse(count.getValue().sql().contains("WHERE"));
        assertTrue(count.getValue().parameters().isEmpty());
    }

    @Test
    void invalidPageAndOversizedPageAreBounded() {
        when(mapper.count(any())).thenReturn(1000L);
        when(mapper.selectRows(any())).thenReturn(List.of());
        var page = service("MYSQL").list(null, -1, 10000);
        assertEquals(1, page.getPageNum());
        assertEquals(100, page.getPageSize());
        var query = ArgumentCaptor.forClass(ParameterizedSql.class);
        verify(mapper).selectRows(query.capture());
        assertEquals(List.of(0L, 100L), values(query.getValue()));
    }

    @Test
    void outOfRangePageKeepsTotalWithoutLoadingRecords() {
        when(mapper.count(any())).thenReturn(20L);
        var page = service("MYSQL").list("OPEN", Integer.MAX_VALUE, 100);
        assertEquals(20L, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        verify(mapper, never()).selectRows(any());
    }

    @Test
    void emptyResultDoesNotLoadRecords() {
        when(mapper.count(any())).thenReturn(0L);
        var page = service("MYSQL").list("RESOLVED", 1, 20);
        assertEquals(0L, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        verify(mapper, never()).selectRows(any());
    }

    private List<Object> values(ParameterizedSql statement) {
        return statement.parameters().stream().map(ParameterizedSql.Parameter::value).toList();
    }
}
