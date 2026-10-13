package com.workflow.entity.excel.config;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ExcelSchemeValidatorTest {
    private final ExcelSchemeValidator validator = new ExcelSchemeValidator();

    static EntityDefinition entity() {
        var entity = new EntityDefinition(); entity.setId("entity1"); entity.setEntityCode("employee");
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC); return entity;
    }
    static EntityField field(String code, EntityField.FieldType type) {
        var field = new EntityField(); field.setFieldCode(code); field.setFieldType(type); field.setFieldName(code);
        field.setEditable(true); return field;
    }
    static ExcelSchemeDocument.Column column(String key, String field, String header, List<String> aliases, Map<String, Object> codec) {
        return new ExcelSchemeDocument.Column(key, header, aliases, field, true, true, true, 20, codec, Map.of());
    }
    static ExcelSchemeDocument document(String code, boolean input, List<ExcelSchemeDocument.Column> columns) {
        return new ExcelSchemeDocument(1, code, code, null,
                new ExcelSchemeDocument.FileConfig("员工", 1, "REJECT"),
                new ExcelSchemeDocument.ImportConfig(input, "CREATE_ONLY", "ATOMIC_ALL", "write", false, 1000),
                new ExcelSchemeDocument.ExportConfig(true, "员工_{yyyyMMddHHmmss}", "员工"), columns);
    }
    static ExcelSchemeDocument valid(String code, boolean input) {
        return document(code, input, List.of(column("name", "name", "姓名", List.of(), Map.of("type", "STRING"))));
    }
    @Test void acceptsOrdinaryFieldsAndAllowsIncompleteDraft() {
        validator.validatePublished(valid("basic", true), entity(), List.of(field("name", EntityField.FieldType.STRING)));
        validator.validateDraft(new ExcelSchemeDocument(1, "basic", "待完善", null, null, null, null, List.of()));
    }
    @Test void validatesFormUnknownPolicySeparatelyFromFileHeaders() {
        var base = valid("basic", true);
        for (String policy : List.of("DIRECT", "REJECT", "IGNORE", "INVALID")) {
            var doc = new ExcelSchemeDocument(1, base.schemeCode(), base.name(), null, base.file(),
                    new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "ATOMIC_ALL", "write", false, 1000, policy),
                    base.exportConfig(), base.columns());
            if ("INVALID".equals(policy)) assertThatThrownBy(() -> validator.validateDraft(doc)).hasMessageContaining("表单未知列策略");
            else validator.validatePublished(doc, entity(), List.of(field("name", EntityField.FieldType.STRING)));
        }
    }
    @Test void newReadOptionsDefaultAndRoundtripWithoutLosingExplicitFalse() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var legacy = mapper.readValue("{\"enabled\":true,\"transactionPolicy\":\"ATOMIC_ALL\"}", ExcelSchemeDocument.ImportConfig.class);
        assertThat(legacy.validateHeader()).isTrue();
        assertThat(legacy.stopAtEmptyRow()).isTrue();
        assertThat(legacy.batchSize()).isEqualTo(100);
        assertThat(legacy.returnUnimported()).isTrue();
        assertThat(legacy.precheckEnabled()).isTrue();
        assertThat(legacy.failurePolicy()).isEqualTo("STOP");
        assertThat(legacy.maxFileSizeMb()).isEqualTo(10);
        assertThat(mapper.readValue("{\"maxFileSizeMb\":null}", ExcelSchemeDocument.ImportConfig.class).maxFileSizeMb())
                .isEqualTo(10);
        var configured = new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "BATCH", "write", false, 1000,
                "DIRECT", false, false, 3, false, false, "CONTINUE", 25);
        assertThat(mapper.readValue(mapper.writeValueAsString(configured), ExcelSchemeDocument.ImportConfig.class)).isEqualTo(configured);
    }
    @Test void fileSizeLimitIsValidatedForDraftAndPublishedDocuments() {
        var base = valid("basic", true);
        for (int size : List.of(-1, 0, 1, 10, 50, 51)) {
            var config = new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "BATCH", "write", false, 1000,
                    "DIRECT", true, true, 100, true, true, "STOP", size);
            var doc = new ExcelSchemeDocument(1, base.schemeCode(), base.name(), null, base.file(), config,
                    base.exportConfig(), base.columns());
            if (size < 1 || size > 50) {
                assertThatThrownBy(() -> validator.validateDraft(doc)).hasMessageContaining("文件大小限制");
                assertThatThrownBy(() -> validator.validatePublished(doc, entity(), List.of(field("name", EntityField.FieldType.STRING))))
                        .hasMessageContaining("文件大小限制");
            } else {
                validator.validateDraft(doc);
                validator.validatePublished(doc, entity(), List.of(field("name", EntityField.FieldType.STRING)));
            }
        }
    }
    @Test void validatesBatchPolicyAndCommitBounds() {
        var base = valid("basic", true);
        for (int size : List.of(0, 1, 1000, 1001)) for (String failure : List.of("STOP", "CONTINUE", "INVALID")) {
            var doc = new ExcelSchemeDocument(1, base.schemeCode(), base.name(), null, base.file(),
                    new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "BATCH", "write", false, 1000,
                            "DIRECT", true, true, size, true, true, failure), base.exportConfig(), base.columns());
            if (size == 0 || size == 1001 || "INVALID".equals(failure))
                assertThatThrownBy(() -> validator.validatePublished(doc, entity(), List.of(field("name", EntityField.FieldType.STRING))))
                        .isInstanceOf(IllegalArgumentException.class);
            else validator.validatePublished(doc, entity(), List.of(field("name", EntityField.FieldType.STRING)));
        }
    }
    @Test void batchHeaderValidationIgnoresLegacyAliasesWithoutRemovingThem() {
        var columns = List.of(column("name", "name", "姓名", List.of("姓名", "编号"), Map.of()),
                column("code", "code", "编号", List.of("姓名"), Map.of()));
        var base = document("basic", true, columns);
        var fields = List.of(field("name", EntityField.FieldType.STRING), field("code", EntityField.FieldType.STRING));
        assertThatThrownBy(() -> validator.validatePublished(base, entity(), fields)).hasMessageContaining("别名重复");
        for (boolean validateHeader : List.of(true, false)) {
            var config = new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "BATCH", "write", false, 1000,
                    "DIRECT", validateHeader, true, 100, true, true, "STOP");
            var batch = new ExcelSchemeDocument(1, base.schemeCode(), base.name(), null, base.file(), config, base.exportConfig(), columns);
            validator.validatePublished(batch, entity(), fields);
            assertThat(batch.columns().get(0).headerAliases()).containsExactly("姓名", "编号");
        }
    }
    @Test void rejectsUnknownPublishedField() {
        assertThatThrownBy(() -> validator.validatePublished(valid("basic", true), entity(), List.of()))
                .hasMessageContaining("已发布实体");
    }
    @Test void rejectsDuplicateHeadersIncludingAliases() {
        var doc = document("basic", true, List.of(
                column("name", "name", "姓名", List.of("名字"), Map.of()),
                column("code", "code", "编号", List.of(" 名字 "), Map.of())));
        assertThatThrownBy(() -> validator.validatePublished(doc, entity(), List.of(field("name", EntityField.FieldType.STRING), field("code", EntityField.FieldType.STRING))))
                .hasMessageContaining("别名重复");
    }
    @Test void rejectsDuplicateInputFields() {
        var doc = document("basic", true, List.of(column("a", "name", "姓名", List.of(), Map.of()), column("b", "name", "别名", List.of(), Map.of())));
        assertThatThrownBy(() -> validator.validatePublished(doc, entity(), List.of(field("name", EntityField.FieldType.STRING))))
                .hasMessageContaining("重复映射");
    }
    @Test void systemEntitiesCannotImport() {
        var entity = entity(); entity.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
        assertThatThrownBy(() -> validator.validatePublished(valid("basic", true), entity, List.of(field("name", EntityField.FieldType.STRING))))
                .hasMessageContaining("系统实体");
    }
    @Test void forbidsSystemAuditWriteEvenWithEditableFlag() {
        var audit = field("createdBy", EntityField.FieldType.STRING); audit.setIsSystem(true);
        var doc = document("basic", true, List.of(column("creator", "createdBy", "创建人", List.of(), Map.of())));
        assertThatThrownBy(() -> validator.validatePublished(doc, entity(), List.of(audit))).hasMessageContaining("不允许导入写入");
    }
    @Test void enforcesConverterFieldCompatibility() {
        var doc = document("basic", false, List.of(column("name", "name", "姓名", List.of(), Map.of("type", "AMOUNT"))));
        assertThatThrownBy(() -> validator.validatePublished(doc, entity(), List.of(field("name", EntityField.FieldType.STRING))))
                .hasMessageContaining("数值字段");
    }
    @Test void dictionaryCodecCannotSelectArbitraryDictionary() {
        var doc = document("basic", false, List.of(column("name", "name", "姓名", List.of(), Map.of("type", "DICT"))));
        assertThatThrownBy(() -> validator.validatePublished(doc, entity(), List.of(field("name", EntityField.FieldType.STRING))))
                .hasMessageContaining("代码表");
    }
    @Test void dateCodecCannotSilentlyTruncateDateTimeField() {
        var doc = document("basic", false, List.of(column("time", "time", "时间", List.of(), Map.of("type", "DATE"))));
        assertThatThrownBy(() -> validator.validatePublished(doc, entity(), List.of(field("time", EntityField.FieldType.DATETIME))))
                .hasMessageContaining("时间被截断");
    }

    @Test void entityStatusNamesAreExportOnlyAndDoNotRequireDictionary() {
        var status = field("status", EntityField.FieldType.STRING);
        status.setIsSystem(true); status.setEditable(false);
        for (Map<String, Object> codec : List.<Map<String, Object>>of(Map.of("type", "ENTITY_STATUS"),
                Map.of("type", "ENTITY_STATUS", "exportMode", "LABEL"),
                Map.of("type", "ENTITY_STATUS", "exportMode", "CODE"))) {
            var column = new ExcelSchemeDocument.Column("status", "状态", List.of(), "status", false, true,
                    false, 20, codec, Map.of());
            assertThatCode(() -> validator.validatePublished(document("status_export", false, List.of(column)),
                    entity(), List.of(status))).doesNotThrowAnyException();
        }
    }

    @Test void entityStatusCodecRejectsOtherFieldsDirectionsAndInvalidMode() {
        // 字段编码相同但不是系统字段，也不能冒用系统状态的值域。
        for (String fieldCode : List.of("status", "processStatus", "businessStatus")) {
            var field = field(fieldCode, EntityField.FieldType.STRING);
            field.setIsSystem(!"status".equals(fieldCode));
            var column = new ExcelSchemeDocument.Column("status", "状态", List.of(), fieldCode, false, true,
                    false, 20, Map.of("type", "ENTITY_STATUS"), Map.of());
            assertThatThrownBy(() -> validator.validatePublished(document("status_export", false, List.of(column)),
                    entity(), List.of(field))).hasMessageContaining("系统状态字段 status");
        }
        var status = field("status", EntityField.FieldType.STRING); status.setIsSystem(true);
        var importing = new ExcelSchemeDocument.Column("status", "状态", List.of(), "status", true, true,
                false, 20, Map.of("type", "ENTITY_STATUS"), Map.of());
        assertThatThrownBy(() -> validator.validatePublished(document("status_import", true, List.of(importing)),
                entity(), List.of(status))).hasMessageContaining("不允许导入写入");
        var invalidMode = new ExcelSchemeDocument.Column("status", "状态", List.of(), "status", false, true,
                false, 20, Map.of("type", "ENTITY_STATUS", "exportMode", "LABEL_OR_CODE"), Map.of());
        assertThatThrownBy(() -> validator.validatePublished(document("status_export", false, List.of(invalidMode)),
                entity(), List.of(status))).hasMessageContaining("实体状态导出模式不合法");
    }

    @Test void acceptsIndependentImportAndExportMappingsForTheSameFields() {
        var fields = List.of(field("name", EntityField.FieldType.STRING), field("code", EntityField.FieldType.STRING));
        var columns = List.of(
                directionalColumn("input-name", "name", "导入姓名", true, false),
                directionalColumn("input-code", "code", "导入编号", true, false),
                directionalColumn("output-code", "code", "输出编号", false, true),
                directionalColumn("output-name", "name", "输出姓名", false, true));
        validator.validatePublished(document("separated", true, columns), entity(), fields);
    }

    @Test void allowsTwoHundredColumnsPerDirectionAfterSplittingLegacyMappings() {
        var fields = new java.util.ArrayList<EntityField>();
        var legacy = new java.util.ArrayList<ExcelSchemeDocument.Column>();
        var split = new java.util.ArrayList<ExcelSchemeDocument.Column>();
        for (int i = 0; i < 200; i++) {
            String field = "field" + i;
            fields.add(field(field, EntityField.FieldType.STRING));
            legacy.add(directionalColumn(field, field, field, true, true));
            split.add(directionalColumn("input-" + field, field, field, true, false));
            split.add(directionalColumn("output-" + field, field, field, false, true));
        }
        // 旧方案每方向已有200列，编辑器拆成独立列后仍必须可发布，而不能因文档条目翻倍被拒绝。
        validator.validatePublished(document("legacy", true, legacy), entity(), fields);
        validator.validatePublished(document("split", true, split), entity(), fields);
    }

    @Test void independentMappingsCannotExceedEitherDirectionLimit() {
        for (boolean importing : List.of(true, false)) {
            var columns = java.util.stream.IntStream.range(0, 201)
                    .mapToObj(i -> directionalColumn("column" + i, "field" + i, "标题" + i, importing, !importing))
                    .toList();
            assertThatThrownBy(() -> validator.validateDraft(document("too_many", true, columns)))
                    .hasMessageContaining((importing ? "导入" : "导出") + "最多配置200列");
        }
    }

    @Test void inactiveMappingsCannotBypassTotalDocumentLimit() {
        var columns = java.util.stream.IntStream.range(0, 401)
                .mapToObj(i -> directionalColumn("column" + i, "field" + i, "标题" + i, false, false))
                .toList();
        assertThatThrownBy(() -> validator.validateDraft(document("too_many", true, columns)))
                .hasMessageContaining("最多配置400条列映射");
    }

    private static ExcelSchemeDocument.Column directionalColumn(
            String key, String field, String header, boolean importing, boolean exporting) {
        return new ExcelSchemeDocument.Column(key, header, List.of(), field, importing, exporting,
                false, 20, Map.of("type", "STRING"), Map.of());
    }
}
