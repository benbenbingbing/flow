package com.workflow.entity.excel.config;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ExcelSchemeValidatorTest {
    private final EntityExcelReferenceMetadata references = org.mockito.Mockito.mock(EntityExcelReferenceMetadata.class);
    private final ExcelSchemeValidator validator = new ExcelSchemeValidator(references);

    @Test
    void referencesRequireDedicatedCodecAndValidateTargetPropertiesAtPublication() {
        var source = field("customer", EntityField.FieldType.REFERENCE);
        var scalar = field("text", EntityField.FieldType.STRING);
        var target = new EntityExcelReferenceMetadata.Target("customers", "客户", false, false, Map.of(
                "code", new EntityExcelReferenceMetadata.Property("code", "编码", EntityField.FieldType.STRING, "code"),
                "name", new EntityExcelReferenceMetadata.Property("name", "名称", EntityField.FieldType.STRING, "name"),
                "email", new EntityExcelReferenceMetadata.Property("email", "邮箱", EntityField.FieldType.STRING, "email")));
        org.mockito.Mockito.when(references.resolve(source)).thenReturn(target);
        var columns = List.of(column("customer", "customer", "客户", List.of(), Map.of("type", "ENTITY_REFERENCE")));
        assertThatCode(() -> validator.validatePublished(document("refs", true, columns), entity(), List.of(source))).doesNotThrowAnyException();
        for (var codec : List.of(Map.<String,Object>of("type", "STRING"),
                Map.<String,Object>of("type", "ENTITY_REFERENCE", "matchFields", List.of("missing")),
                Map.<String,Object>of("type", "ENTITY_REFERENCE", "exportField", "private_field"))) {
            var invalid = document("refs", true, List.of(column("customer", "customer", "客户", List.of(), codec)));
            assertThatThrownBy(() -> validator.validatePublished(invalid, entity(), List.of(source))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> validator.validatePublished(document("refs", true,
                List.of(column("text", "text", "文本", List.of(), Map.of("type", "ENTITY_REFERENCE")))), entity(), List.of(scalar)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void missingDictionaryNamesSchemeDirectionColumnFieldAndRepairLocation() {
        var name = field("name", EntityField.FieldType.STRING);
        var grade = field("grade", EntityField.FieldType.SELECT); grade.setFieldName("客户等级");
        var columns = List.of(directionalColumn("name_in", "name", "客户名称", true, false),
                directionalColumn("name_out", "name", "客户名称", false, true),
                new ExcelSchemeDocument.Column("grade_out", "等级名称", List.of(), "grade", false, true, false, 20, Map.of("type", "DICT"), Map.of()));
        var base = document("customer_export", true, columns);
        var doc = new ExcelSchemeDocument(1, base.schemeCode(), "客户资料", null, base.file(), base.importConfig(), base.exportConfig(), columns);
        assertThatThrownBy(() -> validator.validatePublished(doc, entity(), List.of(name, grade)))
                .hasMessageContaining("方案“客户资料”（customer_export）")
                .hasMessageContaining("导出设置 → B 列“等级名称”")
                .hasMessageContaining("实体字段：“客户等级” / grade")
                .hasMessageContaining("转换规则").hasMessageContaining("未绑定代码表")
                .hasMessageContaining("字段设计").hasMessageContaining("发布实体");
    }

    @Test void systemStatusDictionaryMistakeSuggestsEntityStatusConverter() {
        var status = field("status", EntityField.FieldType.STRING); status.setIsSystem(true); status.setFieldName("状态");
        var doc = document("status_export", false, List.of(new ExcelSchemeDocument.Column("status", "状态名称", List.of(), "status", false, true,
                false, 20, Map.of("type", "DICT"), Map.of())));
        assertThatThrownBy(() -> validator.validatePublished(doc, entity(), List.of(status)))
                .hasMessageContaining("导出设置 → A 列“状态名称”")
                .hasMessageContaining("实体字段：“状态” / status")
                .hasMessageContaining("系统状态字段不使用代码表")
                .hasMessageContaining("改为“实体状态”");
    }

    @Test void malformedCodecAndRuleValuesHaveReadableSettingNames() {
        var date = field("date", EntityField.FieldType.DATE); date.setFieldName("业务日期");
        var invalidDate = document("dates", true, List.of(column("date", "date", "日期", List.of(), Map.of("type", "DATE", "format", "invalid-pattern"))));
        assertThatThrownBy(() -> validator.validatePublished(invalidDate, entity(), List.of(date)))
                .hasMessageContaining("实体字段：“业务日期” / date").hasMessageContaining("转换规则：日期格式无效");
        var number = field("amount", EntityField.FieldType.DECIMAL);
        var invalidScale = document("amounts", true, List.of(column("amount", "amount", "金额", List.of(), Map.of("type", "AMOUNT", "scale", "wrong"))));
        assertThatThrownBy(() -> validator.validatePublished(invalidScale, entity(), List.of(number)))
                .hasMessageContaining("转换规则：小数位数必须为0至18之间的整数");
        var invalidMin = document("amounts", true, List.of(new ExcelSchemeDocument.Column("amount", "金额", List.of(), "amount", true, false,
                false, 20, Map.of("type", "AMOUNT"), Map.of("min", "wrong"))));
        assertThatThrownBy(() -> validator.validatePublished(invalidMin, entity(), List.of(number)))
                .hasMessageContaining("导入设置 → A 列“金额”").hasMessageContaining("附加校验：最小值必须是有效数字");
    }

    @Test void locationUsesIndependentSequencesIncludingLegacyInactiveAndColumnsBeyondZ() {
        var columns = new java.util.ArrayList<ExcelSchemeDocument.Column>();
        columns.add(directionalColumn("before", "before", "先导出", false, true));
        for (int i = 0; i < 26; i++) columns.add(directionalColumn("input" + i, "input" + i, "输入" + i, true, false));
        columns.add(directionalColumn("both", "both", "双向列", true, true));
        columns.add(directionalColumn("inactive", "inactive", "历史列", false, false));
        var doc = document("legacy", true, columns);
        assertThat(ExcelConfigurationErrors.column(doc, 27, field("both", EntityField.FieldType.STRING), "转换规则", "格式无效"))
                .hasMessageContaining("导入设置 → AA 列 / 导出设置 → B 列“双向列”");
        assertThat(ExcelConfigurationErrors.column(doc, 28, null, "字段 / 列标题", "字段不存在"))
                .hasMessageContaining("未启用的历史列 → 第 1 项“历史列”").hasMessageContaining("inactive");
    }

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
