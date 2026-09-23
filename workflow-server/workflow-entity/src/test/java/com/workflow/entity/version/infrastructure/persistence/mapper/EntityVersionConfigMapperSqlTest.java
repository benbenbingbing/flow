package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfigReadRow;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** SQL 只读取相同快照，Java 投影保留有效发布优先及管理占位行的不同契约。 */
class EntityVersionConfigMapperSqlTest {
    @Test
    void joinsOnlyOwnedActiveReleaseWithoutVendorJsonOrPagination() throws Exception {
        for (String method : List.of("selectCurrentRow", "selectCurrentRows")) {
            var parameterTypes = method.equals("selectCurrentRow") ? new Class<?>[]{String.class} : new Class<?>[0];
            String sql = String.join(" ", EntityVersionConfigMapper.class.getMethod(method, parameterTypes).getAnnotation(Select.class).value());
            assertTrue(sql.contains("r.id = c.active_release_id AND r.config_id = c.id"));
            assertTrue(sql.contains("r.config_document AS source_release_document"));
            assertTrue(sql.contains("c.deleted = 0"));
            assertFalse(sql.contains("JSON_"));
            assertFalse(sql.contains("CAST("));
            assertFalse(sql.contains("LIMIT"));
            assertFalse(sql.contains("draft_document"));
        }
    }

    @Test
    void runtimeFiltersEmptyProjectionButManagementPreservesRevision() {
        var mapper = mock(EntityVersionConfigMapper.class, CALLS_REAL_METHODS);
        var empty = row("empty", null);
        empty.setRevision(7);
        var invalid = row("invalid", null);
        invalid.setSourceReleaseId("r1");
        invalid.setSourceReleaseDocument("{bad}");
        var active = row("active", null);
        active.setSourceReleaseId("r2");
        active.setSourceReleaseDocument("{}");
        var current = row("current", "{}");
        when(mapper.selectCurrentRows()).thenReturn(List.of(empty, invalid, active, current));
        assertEquals(List.of("active", "current"), mapper.findAllCurrent().stream().map(r -> r.getId()).toList());
        assertEquals(4, mapper.findAllForManagementList().size());
        assertEquals(7, mapper.findAllForManagementList().get(0).getRevision());
        assertNull(empty.getConfigDocument(), "投影不能就地修改 Mapper 原始行");
    }

    @Test
    void singleReadUsesSameProjectionAndKeepsMissingRowSemantics() {
        var mapper = mock(EntityVersionConfigMapper.class, CALLS_REAL_METHODS);
        assertNull(mapper.findByEntityCode("missing"));
        var row = row("current", "{\"source\":\"current\"}");
        row.setSourceReleaseId("release");
        row.setSourceReleaseDocument("{\"source\":\"release\",\"status\":\"PUBLISHED\"}");
        row.setSourceContractVersion(2);
        when(mapper.selectCurrentRow("asset")).thenReturn(row);
        String effective = mapper.findByEntityCode("asset").getConfigDocument();
        assertTrue(effective.contains("release"));
        assertTrue(effective.contains("\"schemaVersion\":2"));
        assertFalse(effective.contains("PUBLISHED"));
    }

    private EntityVersionConfigReadRow row(String id, String document) {
        var row = new EntityVersionConfigReadRow();
        row.setId(id);
        row.setConfigDocument(document);
        return row;
    }
}
