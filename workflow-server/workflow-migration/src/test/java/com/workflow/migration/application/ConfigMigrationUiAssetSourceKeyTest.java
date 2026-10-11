package com.workflow.migration.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 当前表单摘要必须能登记迁移资产，且不能因截断丢失资产去重维度。 */
class ConfigMigrationUiAssetSourceKeyTest {
    @Test
    void legacySourceCoordinatesAndExactColumnBoundaryRemainCompatible() {
        assertEquals("list-release:v9", ConfigMigrationAssetService.entityUiAssetSourceKey("list-release", 9));
        assertEquals("a".repeat(61) + ":v1", ConfigMigrationAssetService.entityUiAssetSourceKey("a".repeat(61), 1));
    }

    @Test
    void fullFormDigestAndAssetSequenceBothParticipateInBoundedSourceKey() {
        String hash = "a".repeat(64);
        String first = ConfigMigrationAssetService.entityUiAssetSourceKey(hash, 1);
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertNotEquals(hash, first);
        assertEquals(first, ConfigMigrationAssetService.entityUiAssetSourceKey(hash, 1));
        assertNotEquals(first, ConfigMigrationAssetService.entityUiAssetSourceKey(hash, 2));
        assertNotEquals(first, ConfigMigrationAssetService.entityUiAssetSourceKey("a".repeat(63) + "b", 1));
        assertEquals(64, ConfigMigrationAssetService.entityUiAssetSourceKey(hash, Integer.MAX_VALUE).length());
    }
}
