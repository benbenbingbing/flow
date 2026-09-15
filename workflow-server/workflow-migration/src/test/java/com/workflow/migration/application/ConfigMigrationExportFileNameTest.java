package com.workflow.migration.application;

import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationExportFileNameTest {
    private static final String PACKAGE_NO = "WFP-REL-20260915-084526-20260915084614-73A360";

    @ParameterizedTest
    @CsvSource({"ENTITY,实体", "PROCESS,流程", "SYSTEM_ENTITY_UI,系统实体UI", "DICTIONARY,字典",
            "WORK_CALENDAR,工作日历", "TASK_SLA_POLICY,SLA策略"})
    void includesAssetTypeNameCodeVersionAndUniquePackageNumber(String type, String label) {
        String fileName = ConfigMigrationExportFileName.create(PACKAGE_NO,
                List.of(asset(type, "全流程验收", "all_flow", 2)));

        assertEquals(label + "-全流程验收-all_flow-v2_" + PACKAGE_NO + ".wfpack", fileName);
    }

    @Test
    void summarizesFirstTwoAssetsAndTotalForBatchExport() {
        List<ConfigMigrationAsset> assets = List.of(asset("ENTITY", "费用", "expense", 1),
                asset("PROCESS", "审批", "approval", 2), asset("ENTITY", "订单", "order", 3));

        assertEquals("批量-实体-费用-expense-v1_流程-审批-approval-v2-共3项_" + PACKAGE_NO + ".wfpack",
                ConfigMigrationExportFileName.create(PACKAGE_NO, assets));
    }

    @Test
    void removesCharactersInvalidInDownloadPaths() {
        String fileName = ConfigMigrationExportFileName.create(PACKAGE_NO,
                List.of(asset("ENTITY", "../费用\\表单:新\n版?*<>\"|", "expense", 1)));

        assertEquals("实体--费用-表单-新-版-------expense-v1_" + PACKAGE_NO + ".wfpack", fileName);
        assertFalse(fileName.matches(".*[\\p{Cntrl}<>:\"/\\\\|?*].*"));
    }

    @Test
    void boundsChineseAndSupplementaryCharactersWithoutLosingVersionOrUniqueSuffix() {
        String longName = "全流程验收😀".repeat(80);
        String longCode = "entity_long_code_".repeat(20);
        ConfigMigrationAsset asset = asset("ENTITY", longName, longCode, 123);
        for (List<ConfigMigrationAsset> assets : List.of(List.of(asset), List.of(asset, asset))) {
            String fileName = ConfigMigrationExportFileName.create(PACKAGE_NO, assets);

            assertTrue(fileName.getBytes(StandardCharsets.UTF_8).length <= 255);
            assertTrue(fileName.contains("全流程"));
            assertTrue(fileName.contains("entity_long_code_"));
            assertTrue(fileName.contains("-v123"));
            assertTrue(fileName.endsWith("_" + PACKAGE_NO + ".wfpack"));
            assertEquals(fileName, new String(fileName.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        }
    }

    @Test
    void fallsBackToCodeWhenNameIsMissingAndAvoidsDuplicatingIdenticalName() {
        for (String name : new String[] {null, " ", "expense"}) {
            assertEquals("实体-expense-v1_" + PACKAGE_NO + ".wfpack",
                    ConfigMigrationExportFileName.create(PACKAGE_NO,
                            List.of(asset("ENTITY", name, "expense", 1))));
        }
    }

    private ConfigMigrationAsset asset(String type, String name, String code, int version) {
        ConfigMigrationAsset asset = new ConfigMigrationAsset();
        asset.setAssetType(type);
        asset.setAssetName(name);
        asset.setBusinessKey(code);
        asset.setSourceVersion(version);
        return asset;
    }
}
