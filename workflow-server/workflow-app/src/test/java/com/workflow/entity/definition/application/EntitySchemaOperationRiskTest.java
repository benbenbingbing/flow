package com.workflow.entity.definition.application;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.SchemaColumn;
import com.workflow.integration.database.api.SchemaDefault;
import com.workflow.integration.database.api.SchemaType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 大表和破坏性 DDL 必须升级风险等级，不能依赖前端自行判断。 */
class EntitySchemaOperationRiskTest {

    @Test
    void classifiesLargeModifyAsHighRisk() {
        EntitySchemaOperationService.Risk risk = EntitySchemaOperationService.assessRisk(
                List.of("ALTER TABLE `biz_order` MODIFY COLUMN `amount` DECIMAL(20,2)"),
                600_000L,
                List.of(),
                List.of());

        assertEquals("HIGH", risk.level());
        assertEquals("HIGH", risk.lockRisk());
    }

    @Test
    void classifiesSmallCreateAsLowRisk() {
        EntitySchemaOperationService.Risk risk = EntitySchemaOperationService.assessRisk(
                List.of("CREATE TABLE `biz_order` (`id` VARCHAR(64))"),
                0L,
                List.of(),
                List.of());

        assertEquals("LOW", risk.level());
        assertEquals("LOW", risk.lockRisk());
    }

    @Test
    void allRenderedColumnChangesAreHighRiskEvenForAnEmptyTable() {
        var column = new SchemaColumn("amount", SchemaType.decimal(20, 2), true, SchemaDefault.none(), "金额", false);
        for (var vendor : DatabaseVendor.values()) {
            List<String> plan = DatabaseDialects.forVendor(vendor).modifyColumn("biz_order", column);
            EntitySchemaOperationService.Risk risk = EntitySchemaOperationService.assessRisk(plan, 0, List.of(), List.of());
            assertEquals("HIGH", risk.level(), vendor.name());
            assertEquals("HIGH", risk.lockRisk(), vendor.name());

            // PG 的完整计划还含 DROP DEFAULT；单独验证真实 TYPE 语句，防止误靠 DROP 升级风险。
            String typeChange = vendor == DatabaseVendor.POSTGRESQL || vendor == DatabaseVendor.KINGBASE
                    ? plan.stream().filter(sql -> sql.contains(" TYPE ")).findFirst().orElseThrow()
                    : plan.get(0);
            var statementRisk = EntitySchemaOperationService.assessRisk(List.of(typeChange), 0, List.of(), List.of());
            assertEquals("HIGH", statementRisk.level(), vendor + ": " + typeChange);
            assertEquals("HIGH", statementRisk.lockRisk(), vendor.name());
            assertEquals("计划包含可能重建或锁定数据页的字段修改", statementRisk.reason());
        }
    }

    @Test
    void ordinaryAddColumnsRemainMediumAndDefaultTextDoesNotPretendToModifyAColumn() {
        var column = new SchemaColumn("note", SchemaType.string(200), true,
                SchemaDefault.literal("MODIFY COLUMN MODIFY ( ALTER COLUMN TYPE"), "说明", false);
        for (var vendor : DatabaseVendor.values()) {
            var plan = DatabaseDialects.forVendor(vendor).addColumn("biz_order", column);
            var risk = EntitySchemaOperationService.assessRisk(plan, 0, List.of(), List.of());
            assertEquals("MEDIUM", risk.level(), vendor.name());
            assertEquals("MEDIUM", risk.lockRisk(), vendor.name());
        }
    }
}
