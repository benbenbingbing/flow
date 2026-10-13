package com.workflow.entity.excel.config;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/** 在发布边界验证动态列协议；实体字段来源必须是已发布快照，不能信任浏览器提供的类型。 */
@Component
public class ExcelSchemeValidator {
    private static final Set<EntityField.FieldType> SIMPLE_TYPES = Set.of(
            EntityField.FieldType.STRING, EntityField.FieldType.TEXT, EntityField.FieldType.INTEGER,
            EntityField.FieldType.LONG, EntityField.FieldType.DECIMAL, EntityField.FieldType.DATE,
            EntityField.FieldType.DATETIME, EntityField.FieldType.BOOLEAN, EntityField.FieldType.SELECT,
            EntityField.FieldType.RADIO);
    private static final Set<String> CODECS = Set.of("STRING", "DATE", "DATETIME", "AMOUNT", "NUMBER", "BOOLEAN", "DICT", "ENTITY_STATUS");

    /** 保存允许未完成的映射草稿，但稳定身份和协议边界必须始终合法。 */
    public void validateDraft(ExcelSchemeDocument doc) {
        require(doc != null, "方案不能为空");
        require(doc.schemaVersion() == 1, "不支持的 Excel 方案协议版本");
        require(doc.schemeCode() != null && doc.schemeCode().matches("[A-Za-z][A-Za-z0-9_]{0,99}"), "方案编码必须为字母开头的字母、数字或下划线，最多100字符");
        require(doc.name() != null && !doc.name().isBlank() && doc.name().length() <= 200, "方案名称不能为空且不能超过200字符");
        if (doc.importConfig() != null) {
            require(Set.of("DIRECT", "REJECT", "IGNORE").contains(doc.importConfig().unknownColumnPolicy()),
                    "表单未知列策略必须为 DIRECT、REJECT 或 IGNORE");
            // 保存草稿和配置包导入也要验证，避免发布后上传时才触发传输层上限。
            require(doc.importConfig().maxFileSizeMb() >= 1
                            && doc.importConfig().maxFileSizeMb() <= ExcelSchemeDocument.ImportConfig.MAX_FILE_SIZE_MB,
                    "文件大小限制必须在1至50 MB之间");
        }
        // 同一字段可分别定义导入和导出列，拆分后文档条目会翻倍，但每个实际文件仍最多200列。
        require(doc.columns().size() <= 400, "单个方案最多配置400条列映射");
        require(doc.columns().stream().filter(ExcelSchemeDocument.Column::importEnabled).count() <= 200,
                "导入最多配置200列");
        require(doc.columns().stream().filter(ExcelSchemeDocument.Column::exportEnabled).count() <= 200,
                "导出最多配置200列");
    }

    /** 验证方案可执行性；新增导入支持分批提交，并兼容历史整文件原子事务。 */
    public void validatePublished(ExcelSchemeDocument doc, EntityDefinition entity, List<EntityField> fields) {
        validateDraft(doc);
        require(doc.file() != null && doc.importConfig() != null && doc.exportConfig() != null, "文件、导入和导出设置不能为空");
        require(doc.importConfig().enabled() || doc.exportConfig().enabled(), "方案至少启用导入或导出");
        sheetName(doc.file().sheetName());
        require(doc.file().headRowNumber() == 1, "第一版使用单行表头，请设置表头行数为1");
        require(doc.file().unknownColumnPolicy() != null && Set.of("REJECT", "IGNORE").contains(doc.file().unknownColumnPolicy()), "文件未映射列策略必须为 REJECT 或 IGNORE");
        if (doc.importConfig().enabled()) {
            require(entity.getStorageMode() != EntityDefinition.StorageMode.SYSTEM, "系统实体不能使用通用 Excel 导入");
            require("CREATE_ONLY".equals(doc.importConfig().writePolicy()), "第一版仅支持新增导入");
            require(doc.importConfig().transactionPolicy() != null && Set.of("ATOMIC_ALL", "BATCH").contains(doc.importConfig().transactionPolicy()), "导入事务模式必须为 ATOMIC_ALL 或 BATCH");
            require(Set.of("STOP", "CONTINUE").contains(doc.importConfig().failurePolicy()), "失败策略必须为 STOP 或 CONTINUE");
            require(doc.importConfig().batchSize() > 0 && doc.importConfig().batchSize() <= 1000, "提交条数必须在1至1000之间");
            require(!doc.importConfig().startProcess(), "Excel 导入暂不支持自动发起流程");
            require(doc.importConfig().maxRows() > 0 && doc.importConfig().maxRows() <= 1000, "每次导入行数必须在1至1000之间");
            require(doc.importConfig().writeFormKey() != null && !doc.importConfig().writeFormKey().isBlank(), "请选择已发布的写入表单");
        }
        if (doc.exportConfig().enabled()) {
            sheetName(doc.exportConfig().sheetName());
            String name = doc.exportConfig().fileNamePattern();
            require(name != null && !name.isBlank() && name.length() <= 180 && !name.matches(".*[\\\\/:*?\"<>|\\r\\n].*"), "导出文件名不能为空，且不能包含路径或非法字符");
        }
        Map<String, EntityField> byCode = fields.stream().collect(Collectors.toMap(EntityField::getFieldCode, Function.identity()));
        Set<String> keys = new HashSet<>(), inputFields = new HashSet<>(), inputHeaders = new HashSet<>();
        int inputs = 0, outputs = 0;
        for (var column : doc.columns()) {
            require(column.key() != null && !column.key().isBlank() && keys.add(column.key()), "列标识不能为空或重复");
            require(column.header() != null && !column.header().isBlank() && column.header().length() <= 200, "列标题不能为空且最多200字符");
            EntityField field = byCode.get(column.fieldCode());
            require(field != null, "方案字段不存在于已发布实体: " + column.fieldCode());
            require(field.getFieldType() != null && SIMPLE_TYPES.contains(field.getFieldType()), "第一版不支持复杂字段: " + column.fieldCode());
            require(column.width() == null || (column.width() >= 1 && column.width() <= 255), "列宽必须在1至255之间");
            if (column.importEnabled()) {
                inputs++;
                require(inputFields.add(column.fieldCode()), "同一字段不能重复映射导入列: " + column.fieldCode());
                require(!Boolean.FALSE.equals(field.getEditable())
                        && (!Boolean.TRUE.equals(field.getIsSystem()) || Set.of("name", "code").contains(field.getFieldCode())), "字段不允许导入写入: " + column.fieldCode());
                require(inputHeaders.add(column.header().strip()), "导入标题或别名重复: " + column.header());
                // 分批模式仅使用当前标题或位置，旧别名随文档保留但不再参与映射与发布校验。
                if (!"BATCH".equals(doc.importConfig().transactionPolicy())) for (String alias : column.headerAliases()) {
                    require(alias != null && !alias.isBlank() && inputHeaders.add(alias.strip()), "导入标题或别名重复: " + alias);
                }
            }
            if (column.exportEnabled()) outputs++;
            validateCodec(column, field);
            validateRules(column.validation());
        }
        require(!doc.importConfig().enabled() || inputs > 0, "导入方案至少需要一列输入字段");
        require(!doc.exportConfig().enabled() || outputs > 0, "导出方案至少需要一列输出字段");
    }

    private void validateCodec(ExcelSchemeDocument.Column column, EntityField field) {
        Map<String, Object> codec = column.codec();
        String type = String.valueOf(codec.getOrDefault("type", "STRING"));
        require(CODECS.contains(type), "不支持的转换器: " + type);
        if (Set.of("DATE", "DATETIME").contains(type)) {
            require(("DATE".equals(type) && field.getFieldType() == EntityField.FieldType.DATE)
                    || ("DATETIME".equals(type) && field.getFieldType() == EntityField.FieldType.DATETIME),
                    "日期字段必须使用 DATE，日期时间字段必须使用 DATETIME，避免时间被截断");
            if (codec.get("format") != null) DateTimeFormatter.ofPattern(String.valueOf(codec.get("format")));
            if (codec.get("zoneId") != null) ZoneId.of(String.valueOf(codec.get("zoneId")));
            if (codec.get("readPatterns") instanceof List<?> patterns) {
                require(patterns.size() <= 10, "日期读取格式最多10个");
                patterns.forEach(pattern -> DateTimeFormatter.ofPattern(String.valueOf(pattern)));
            }
        }
        if (Set.of("AMOUNT", "NUMBER").contains(type)) {
            require(Set.of(EntityField.FieldType.INTEGER, EntityField.FieldType.LONG, EntityField.FieldType.DECIMAL).contains(field.getFieldType()), "数值转换器只能绑定数值字段");
            if (codec.get("scale") != null) {
                int scale = new BigDecimal(String.valueOf(codec.get("scale"))).intValueExact();
                require(scale >= 0 && scale <= 18, "金额小数位数必须在0至18之间");
            }
            if (codec.get("roundingMode") != null) RoundingMode.valueOf(String.valueOf(codec.get("roundingMode")));
        }
        if ("DICT".equals(type)) {
            require(field.getDictType() != null && !field.getDictType().isBlank(), "字典转换器必须绑定有代码表的实体字段");
            require(Set.of("CODE", "LABEL", "LABEL_OR_CODE").contains(String.valueOf(codec.getOrDefault("importMode", "LABEL_OR_CODE"))), "字典导入模式不合法");
            require(Set.of("CODE", "LABEL").contains(String.valueOf(codec.getOrDefault("exportMode", "LABEL"))), "字典导出模式不合法");
        }
        if ("ENTITY_STATUS".equals(type)) {
            // 状态由实体/流程保存链维护，显示名称转换不能成为从 Excel 改写状态的入口。
            require(!column.importEnabled() && column.exportEnabled(), "实体状态转换器仅支持导出");
            require("status".equals(field.getFieldCode()) && Boolean.TRUE.equals(field.getIsSystem()),
                    "实体状态转换器只能绑定系统状态字段 status");
            require(Set.of("CODE", "LABEL").contains(String.valueOf(codec.getOrDefault("exportMode", "LABEL"))),
                    "实体状态导出模式不合法");
        }
    }

    private void validateRules(Map<String, Object> rules) {
        BigDecimal min = decimal(rules.get("min")), max = decimal(rules.get("max"));
        require(min == null || max == null || min.compareTo(max) <= 0, "附加校验最小值不能大于最大值");
        if (rules.get("pattern") != null) {
            String pattern = String.valueOf(rules.get("pattern"));
            require(pattern.length() <= 500, "附加校验正则最多500字符");
            Pattern.compile(pattern);
        }
    }
    private BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(String.valueOf(value)); }
    private void sheetName(String name) {
        require(name != null && !name.isBlank() && name.length() <= 31 && !name.matches(".*[\\\\/*?:\\[\\]].*")
                && !name.startsWith("'") && !name.endsWith("'"), "Sheet 名称必须为1至31字符且不能包含 Excel 禁用字符");
    }
    private void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
}
