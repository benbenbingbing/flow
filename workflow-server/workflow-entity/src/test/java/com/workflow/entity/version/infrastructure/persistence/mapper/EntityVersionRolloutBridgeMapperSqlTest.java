package com.workflow.entity.version.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 滚动升级桥的不可变 release 与草稿隔离 SQL 契约测试。 */
class EntityVersionRolloutBridgeMapperSqlTest {

    @Test
    void bridgeNeverUpdatesAnExistingReleaseDocument() {
        boolean updatesReleaseTable = Arrays.stream(
                        EntityVersionRolloutBridgeMapper.class
                                .getDeclaredMethods())
                .map(method -> method.getAnnotation(Update.class))
                .filter(annotation -> annotation != null)
                .map(annotation -> String.join(" ", annotation.value())
                        .toLowerCase())
                .anyMatch(sql -> sql.contains(
                        "update entity_version_config_release"));

        assertFalse(updatesReleaseTable);
    }

    @Test
    void eachCurrentSaveInsertsANewReleaseBeforePointerActivation()
            throws Exception {
        Method insertMethod = EntityVersionRolloutBridgeMapper.class
                .getMethod(
                        "insertCompatibilityRelease",
                        String.class,
                        String.class,
                        Integer.class,
                        Integer.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class);
        Insert insert = insertMethod.getAnnotation(Insert.class);
        String sql = String.join(" ", insert.value()).toLowerCase();

        assertTrue(sql.contains(
                "insert into entity_version_config_release"));
        assertTrue(sql.contains("config_document"));
    }

    @Test
    void legacyDraftCasDoesNotTouchCurrentDocument() throws Exception {
        Method updateMethod = EntityVersionRolloutBridgeMapper.class
                .getMethod(
                        "updateLegacyDraftIfRevision",
                        String.class,
                        Integer.class,
                        Boolean.class,
                        Integer.class,
                        String.class,
                        String.class);
        Update update = updateMethod.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).toLowerCase();

        assertTrue(sql.contains("revision = revision + 1"));
        assertTrue(sql.contains("status = 'draft'"));
        assertFalse(sql.contains("config_document ="));
        assertFalse(sql.contains("active_release_id ="));
    }

    @Test
    void currentReadsPreferValidActiveReleaseAndIgnoreLegacyDraft()
            throws Exception {
        for (Method method : new Method[]{
                EntityVersionConfigMapper.class.getMethod(
                        "findByEntityCode", String.class),
                EntityVersionConfigMapper.class.getMethod("findAllCurrent")
        }) {
            Select select = method.getAnnotation(Select.class);
            String sql = String.join(" ", select.value()).toLowerCase();

            assertTrue(sql.contains(
                    "left join entity_version_config_release"));
            assertTrue(sql.contains("json_valid(r.config_document) = 1"));
            assertTrue(sql.contains("coalesce(r.contract_version, 1)"));
            assertTrue(sql.contains("else c.config_document"));
            assertFalse(sql.contains("draft_document"));
        }
    }
}
