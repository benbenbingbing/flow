package com.workflow.entity.version.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** N 阶段 current 查询的 active release 优先级 SQL 契约测试。 */
class EntityVersionConfigMapperSqlTest {

    @Test
    void singleCurrentReadPrefersValidActiveReleaseWithoutReadingDraft()
            throws Exception {
        String sql = selectSql(EntityVersionConfigMapper.class.getMethod(
                "findByEntityCode", String.class));

        assertCurrentProjection(sql);
        assertTrue(sql.contains("where c.entity_code = #{entitycode}"));
    }

    @Test
    void allCurrentReadIncludesReleaseBackedRowsWithNullCurrentDocument()
            throws Exception {
        String sql = selectSql(EntityVersionConfigMapper.class.getMethod(
                "findAllCurrent"));

        assertCurrentProjection(sql);
        assertTrue(sql.contains("c.config_document is not null"));
        assertTrue(sql.contains("or (r.id is not null"));
    }

    private void assertCurrentProjection(String sql) {
        assertTrue(sql.contains(
                "left join entity_version_config_release r"));
        assertTrue(sql.contains("r.id = c.active_release_id"));
        assertTrue(sql.contains("r.config_id = c.id"));
        assertTrue(sql.contains("json_valid(r.config_document) = 1"));
        assertTrue(sql.contains("cast(r.config_document as json)"));
        assertTrue(sql.contains("'$.schemaversion'"));
        assertTrue(sql.contains("coalesce(r.contract_version, 1)"));
        assertTrue(sql.contains("'$.status'"));
        assertTrue(sql.contains("'$.migrationstate'"));
        assertTrue(sql.contains("'$.activereleaseid'"));
        assertTrue(sql.contains("'$.activereleaseversion'"));
        assertTrue(sql.contains("else c.config_document"));
        assertFalse(sql.contains("draft_document"));
        assertFalse(sql.contains("select *"));
    }

    private String selectSql(Method method) {
        Select select = method.getAnnotation(Select.class);
        return String.join(" ", select.value())
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
