package com.workflow.excel.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workflow.excel.annotation.validation.ExcelEmail;
import com.workflow.excel.annotation.validation.ExcelMax;
import com.workflow.excel.annotation.validation.ExcelMin;
import com.workflow.excel.annotation.validation.ExcelNotBlank;
import com.workflow.excel.annotation.validation.ExcelNotEmpty;
import com.workflow.excel.annotation.validation.ExcelNotNull;
import com.workflow.excel.annotation.validation.ExcelPattern;
import com.workflow.excel.annotation.validation.ExcelSize;
import jakarta.validation.UnexpectedTypeException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.groups.Default;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExcelValidatorTest {
    @Test
    void acceptsValidValuesForAllCommonConstraints() {
        assertTrue(ExcelValidator.validate(validRow()).isEmpty());
    }

    @Test
    void reportsAllInvalidFieldsWithCustomMessagesAndAttributeInterpolation() {
        CommonRow row = validRow();
        row.id = null;
        row.name = " \t";
        row.tags = List.of();
        row.code = "x";
        row.identifier = "ABC123";
        row.email = "bad@";
        row.lowerAmount = new BigDecimal("0.10");
        row.upperAmount = new BigDecimal("100.20");

        List<ExcelValidationError> errors = ExcelValidator.validate(row);

        assertEquals(List.of("code", "email", "id", "identifier", "lowerAmount", "name", "tags", "upperAmount"),
                errors.stream().map(ExcelValidationError::fieldName).toList());
        assertEquals("编码长度在 2 到 4 之间", errors.get(0).message());
        assertEquals("编号必填", errors.get(2).message());
        assertEquals("必须满足最小值 0.10，包含边界：false", errors.get(4).message());
        assertThrows(UnsupportedOperationException.class, () -> errors.clear());
    }

    @Test
    void optionalFormatAndSizeConstraintsAcceptNull() {
        CommonRow row = validRow();
        row.code = null;
        row.identifier = null;
        row.email = null;
        row.lowerAmount = null;
        row.upperAmount = null;

        assertTrue(ExcelValidator.validate(row).isEmpty());
    }

    @Test
    void comparesDecimalBoundariesWithoutFloatingPointRounding() {
        CommonRow row = validRow();
        row.lowerAmount = new BigDecimal("0.10000000000000000001");
        row.upperAmount = new BigDecimal("100.19999999999999999999");
        assertTrue(ExcelValidator.validate(row).isEmpty());

        row.lowerAmount = new BigDecimal("0.09999999999999999999");
        row.upperAmount = new BigDecimal("100.20000000000000000001");
        assertEquals(List.of("lowerAmount", "upperAmount"),
                ExcelValidator.validate(row).stream().map(ExcelValidationError::fieldName).toList());
    }

    @Test
    void supportsInheritedConstraintsAndExplicitValidationGroups() {
        GroupRow row = new GroupRow();
        assertEquals(List.of("defaultValue"),
                ExcelValidator.validate(row).stream().map(ExcelValidationError::fieldName).toList());
        assertEquals(List.of("inheritedValue"),
                ExcelValidator.validate(row, ImportChecks.class).stream().map(ExcelValidationError::fieldName).toList());
        assertEquals(List.of("defaultValue", "inheritedValue"),
                ExcelValidator.validate(row, Default.class, ImportChecks.class)
                        .stream().map(ExcelValidationError::fieldName).toList());
    }

    @Test
    void worksWithStandardJakartaValidatorAndRegexFlags() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(new RegexRow("abc")).isEmpty());
            assertFalse(factory.getValidator().validate(new RegexRow("prefixABC")).isEmpty());
        }
    }

    @Test
    void supportsEmptyCollectionsMapsAndArraysAndGetterConstraints() {
        assertEquals(List.of("items", "mapping", "values"),
                ExcelValidator.validate(new ContainerRow()).stream().map(ExcelValidationError::fieldName).toList());
        assertEquals(List.of("label"),
                ExcelValidator.validate(new GetterRow()).stream().map(ExcelValidationError::fieldName).toList());
    }

    @Test
    void keepsNestedPropertyPathAndOriginalFileRowInException() {
        NestedRow row = new NestedRow();
        ExcelValidationException error = assertThrows(ExcelValidationException.class,
                () -> ExcelValidator.validateOrThrow(row, "人员", 4));

        assertEquals("人员", error.getSheetName());
        assertEquals(4, error.getRowIndex());
        assertEquals(List.of("child.label"), error.getErrors().stream().map(ExcelValidationError::fieldName).toList());
        assertTrue(error.getMessage().contains("第 5 行"));
        assertThrows(UnsupportedOperationException.class, () -> error.getErrors().clear());
    }

    @Test
    void propagatesInvalidConstraintTypeAsConfigurationError() {
        assertThrows(UnexpectedTypeException.class, () -> ExcelValidator.validate(new InvalidTypeRow()));
    }

    private static CommonRow validRow() {
        CommonRow row = new CommonRow();
        row.id = 1L;
        row.name = "张三";
        row.tags = List.of("人员");
        row.code = "AB12";
        row.identifier = "AB12";
        row.email = "user@example.com";
        row.lowerAmount = new BigDecimal("1.25");
        row.upperAmount = new BigDecimal("99.99");
        return row;
    }

    private static class CommonRow {
        @ExcelNotNull(message = "编号必填")
        private Long id;
        @ExcelNotBlank
        private String name;
        @ExcelNotEmpty
        private List<String> tags;
        @ExcelSize(min = 2, max = 4, message = "编码长度在 {min} 到 {max} 之间")
        private String code;
        @ExcelPattern(regexp = "[A-Z]{2}[0-9]{2}")
        private String identifier;
        @ExcelEmail
        private String email;
        @ExcelMin(value = "0.10", inclusive = false)
        private BigDecimal lowerAmount;
        @ExcelMax(value = "100.20", inclusive = false)
        private BigDecimal upperAmount;
    }

    private interface ImportChecks {
    }

    private static class ParentRow {
        @ExcelNotBlank(groups = ImportChecks.class)
        private String inheritedValue;
    }

    private static class GroupRow extends ParentRow {
        @ExcelNotBlank
        private String defaultValue;
    }

    private static class RegexRow {
        @ExcelPattern(regexp = "[A-Z]{3}", flags = Pattern.Flag.CASE_INSENSITIVE)
        private final String value;

        private RegexRow(String value) {
            this.value = value;
        }
    }

    private static class ContainerRow {
        @ExcelNotEmpty
        private List<String> items = List.of();
        @ExcelNotEmpty
        private Map<String, String> mapping = Map.of();
        @ExcelNotEmpty
        private String[] values = {};
    }

    private static class GetterRow {
        @ExcelNotBlank
        public String getLabel() {
            return " ";
        }
    }

    private static class NestedRow {
        @Valid
        private GetterRow child = new GetterRow();
    }

    private static class InvalidTypeRow {
        @ExcelNotBlank
        private Integer invalid = 1;
    }
}
