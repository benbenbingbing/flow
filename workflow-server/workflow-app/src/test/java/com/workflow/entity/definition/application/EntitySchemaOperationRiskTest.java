package com.workflow.entity.definition.application;

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
}
