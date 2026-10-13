package com.workflow.integration.database;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.schema.SchemaType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** OB 的 NLS 参数写法仅作用于该模式，其他产品保留已有数值转换规则。 */
class OceanBaseOracleNumericPatternTest {
    @Test void keepsExplicitDecimalPrecisionAndChangesOnlyOceanBaseNlsSyntax() {
        var decimal = SchemaType.decimal(12, 4);
        var oceanBase = DatabaseQueryDialects.forVendor(DatabaseVendor.OCEANBASE_ORACLE);
        assertEquals("TO_CHAR(\"AMOUNT\", 'FM99999990.0000', 'NLS_NUMERIC_CHARACTERS = .,')",
                oceanBase.patternValueExpression("amount", decimal));
        for (var vendor : new DatabaseVendor[]{DatabaseVendor.ORACLE, DatabaseVendor.DM}) {
            assertEquals("TO_CHAR(\"AMOUNT\", 'FM99999990.0000', 'NLS_NUMERIC_CHARACTERS=''.,''')",
                    DatabaseQueryDialects.forVendor(vendor).patternValueExpression("amount", decimal));
        }
        assertEquals("TO_CHAR(\"ID\", 'FM9999999999999999990', 'NLS_NUMERIC_CHARACTERS = .,')",
                oceanBase.patternValueExpression("id", SchemaType.of(SchemaType.Kind.LONG)));
        assertEquals("TO_CHAR(\"PART\", 'FM0.00000', 'NLS_NUMERIC_CHARACTERS = .,')",
                oceanBase.patternValueExpression("part", SchemaType.decimal(5, 5)));
    }
}
