package com.workflow.entity.excel.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 实体 Excel 方案协议。字段身份由已发布实体定义解析，文档不得包含 SQL 或数据库列名。 */
public record ExcelSchemeDocument(
        int schemaVersion, String schemeCode, String name, String description,
        FileConfig file, ImportConfig importConfig, ExportConfig exportConfig,
        List<Column> columns) {
    public ExcelSchemeDocument {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    /** unknownColumnPolicy 仅处理文件中未匹配方案标题/别名的列，保留旧文档语义。 */
    public record FileConfig(String sheetName, int headRowNumber, String unknownColumnPolicy) {}
    /**
     * 写入表单属于方案实体；发布时解析成稳定 formId，运行时不接受客户端替换。
     * unknownColumnPolicy 处理方案已映射、但写入表单未配置的实体字段；缺省 DIRECT 保持历史可写行为。
     * BATCH 使用 batchSize 控制每次事务提交数量，failurePolicy 决定失败后是否继续。
     * validateHeader 打开时按标题及顺序核验，关闭后按位置读值；stopAtEmptyRow 避免导入间隔后的备注数据。
     * returnUnimported 决定是否提供包含原始整行及失败原因的文件；precheckEnabled 控制只校验不保存的入口。
     * maxFileSizeMb 按 1024² 字节限制导入和预校验的上传文件，旧方案缺省 10；前后端使用同一发布值。
     */
    public record ImportConfig(boolean enabled, String writePolicy, String transactionPolicy,
                               String writeFormKey, boolean startProcess, int maxRows, String unknownColumnPolicy,
                               Boolean validateHeader, Boolean stopAtEmptyRow, Integer batchSize,
                               Boolean returnUnimported, Boolean precheckEnabled, String failurePolicy,
                               Integer maxFileSizeMb) {
        public static final int DEFAULT_MAX_FILE_SIZE_MB = 10;
        public static final int MAX_FILE_SIZE_MB = 50;

        public ImportConfig {
            unknownColumnPolicy = unknownColumnPolicy == null ? "DIRECT" : unknownColumnPolicy;
            validateHeader = validateHeader == null ? true : validateHeader;
            stopAtEmptyRow = stopAtEmptyRow == null ? true : stopAtEmptyRow;
            batchSize = batchSize == null ? 100 : batchSize;
            returnUnimported = returnUnimported == null ? true : returnUnimported;
            precheckEnabled = precheckEnabled == null ? true : precheckEnabled;
            failurePolicy = failurePolicy == null ? "STOP" : failurePolicy;
            maxFileSizeMb = maxFileSizeMb == null ? DEFAULT_MAX_FILE_SIZE_MB : maxFileSizeMb;
        }
        /** 未指定文件限制的旧调用方与缺失该字段的历史 JSON 一样，使用 10 MB 默认上限。 */
        public ImportConfig(boolean enabled, String writePolicy, String transactionPolicy,
                            String writeFormKey, boolean startProcess, int maxRows, String unknownColumnPolicy,
                            Boolean validateHeader, Boolean stopAtEmptyRow, Integer batchSize,
                            Boolean returnUnimported, Boolean precheckEnabled, String failurePolicy) {
            this(enabled, writePolicy, transactionPolicy, writeFormKey, startProcess, maxRows, unknownColumnPolicy,
                    validateHeader, stopAtEmptyRow, batchSize, returnUnimported, precheckEnabled, failurePolicy,
                    DEFAULT_MAX_FILE_SIZE_MB);
        }
        /** 旧调用方沿用其事务模式；新开关缺省值用于重新编辑和发布后的分批导入。 */
        public ImportConfig(boolean enabled, String writePolicy, String transactionPolicy,
                            String writeFormKey, boolean startProcess, int maxRows, String unknownColumnPolicy) {
            this(enabled, writePolicy, transactionPolicy, writeFormKey, startProcess, maxRows, unknownColumnPolicy,
                    true, true, 100, true, true, "STOP");
        }
        /** 旧调用方没有表单外字段策略时，按直接插入兼容。 */
        public ImportConfig(boolean enabled, String writePolicy, String transactionPolicy,
                            String writeFormKey, boolean startProcess, int maxRows) {
            this(enabled, writePolicy, transactionPolicy, writeFormKey, startProcess, maxRows, "DIRECT");
        }
    }
    public record ExportConfig(boolean enabled, String fileNamePattern, String sheetName) {}
    /**
     * 各方向过滤后按数组顺序读写；同一字段可用两个唯一 key 分别配置导入和导出标题、转换规则及顺序。
     * 保留双方向为 true 的旧列兼容历史方案；标题别名仅用于输入，输出始终使用 header。
     */
    public record Column(String key, String header, List<String> headerAliases, String fieldCode,
                         boolean importEnabled, boolean exportEnabled, boolean requiredHeader,
                         Integer width, Map<String, Object> codec, Map<String, Object> validation) {
        public Column {
            headerAliases = headerAliases == null ? List.of() : List.copyOf(headerAliases);
            codec = immutable(codec);
            validation = immutable(validation);
        }
        private static Map<String, Object> immutable(Map<String, Object> value) {
            return value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
        }
    }
}
