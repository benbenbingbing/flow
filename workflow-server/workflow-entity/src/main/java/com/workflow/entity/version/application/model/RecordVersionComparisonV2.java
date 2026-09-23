package com.workflow.entity.version.application.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 类型化的表单式版本比较契约。
 *
 * @param contractVersion 契约版本，保存在对象中供后续校验、查询或展示
 * @param compatibilityMode 兼容性模式标识，决定后续记录版本比较{@code v2}采用的处理分支
 * @param fromVersion 起始版本，保存在对象中供后续校验、查询或展示
 * @param toVersion 截止版本，保存在对象中供后续校验、查询或展示
 * @param summary 摘要，保存在对象中供后续校验、查询或展示
 * @param diffPolicy 差异策略，保存在对象中供后续校验、查询或展示
 * @param nodes 节点集合，保存在对象中供后续校验、查询或展示
 * @param warnings {@code warnings}，保存在对象中供后续校验、查询或展示
 */
public record RecordVersionComparisonV2(
        int contractVersion,
        String compatibilityMode,
        VersionSide fromVersion,
        VersionSide toVersion,
        ComparisonSummary summary,
        EntityVersionConfiguration.DiffPolicy diffPolicy,
        List<NodeComparison> nodes,
        List<String> warnings) {

    /**
     * 初始化记录版本比较{@code v2}，保存构造参数供后续方法使用。
     *
     * @param contractVersion 契约版本，保存在对象中供后续校验、查询或展示
     * @param compatibilityMode 兼容性模式标识，决定后续记录版本比较{@code v2}采用的处理分支
     * @param fromVersion 起始版本，保存在对象中供后续校验、查询或展示
     * @param toVersion 截止版本，保存在对象中供后续校验、查询或展示
     * @param summary 摘要，保存在对象中供后续校验、查询或展示
     * @param diffPolicy 差异策略，保存在对象中供后续校验、查询或展示
     * @param nodes 节点集合，保存在对象中供后续校验、查询或展示
     * @param warnings {@code warnings}，保存在对象中供后续校验、查询或展示
     */
    public RecordVersionComparisonV2 {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * 封装版本侧的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param versionNo 版本号，保存在对象中供后续校验、查询或展示
     * @param versionTitle 版本{@code title}，后续用于处理版本侧时匹配或展示
     * @param scenarioCode {@code scenario}编码，后续用于处理版本侧时定位或关联目标
     * @param scenarioName {@code scenario}名称，后续用于处理版本侧时匹配或展示
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityName 实体名称，后续用于处理版本侧时匹配或展示
     * @param schemaVersion 结构版本，保存在对象中供后续校验、查询或展示
     * @param scopeHash 作用域哈希，保存在对象中供后续校验、查询或展示
     * @param capturedAt {@code captured}时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record VersionSide(
            Integer versionNo,
            String versionTitle,
            String scenarioCode,
            String scenarioName,
            String entityCode,
            String entityName,
            Integer schemaVersion,
            String scopeHash,
            LocalDateTime capturedAt) {
    }

    /**
     * 封装比较摘要的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param dataChangedCount 数据已变更数量，保存在对象中供后续校验、查询或展示
     * @param displayChangedCount 展示已变更数量，保存在对象中供后续校验、查询或展示
     * @param schemaChangedCount 结构已变更数量，保存在对象中供后续校验、查询或展示
     * @param addedRowCount {@code added}行数量，保存在对象中供后续校验、查询或展示
     * @param removedRowCount {@code removed}行数量，保存在对象中供后续校验、查询或展示
     * @param modifiedRowCount {@code modified}行数量，保存在对象中供后续校验、查询或展示
     * @param movedRowCount {@code moved}行数量，保存在对象中供后续校验、查询或展示
     * @param scopeChanged 作用域已变更，保存在对象中供后续校验、查询或展示
     * @param hasChanges {@code has}变更集合，保存在对象中供后续校验、查询或展示
     */
    public record ComparisonSummary(
            int dataChangedCount,
            int displayChangedCount,
            int schemaChangedCount,
            int addedRowCount,
            int removedRowCount,
            int modifiedRowCount,
            int movedRowCount,
            boolean scopeChanged,
            boolean hasChanges) {
    }

    /**
     * 封装节点比较的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param nodeCode 节点编码，后续用于处理节点比较时定位或关联目标
     * @param nodeKind 节点类型，保存在对象中供后续校验、查询或展示
     * @param oldRelationName 旧关系名称，后续用于处理节点比较时匹配或展示
     * @param newRelationName 新关系名称，后续用于处理节点比较时匹配或展示
     * @param displayName 用户可见名称，供界面和日志展示
     * @param oldEntityName 旧实体名称，后续用于处理节点比较时匹配或展示
     * @param newEntityName 新实体名称，后续用于处理节点比较时匹配或展示
     * @param comparability {@code comparability}，保存在对象中供后续校验、查询或展示
     * @param formSections 表单区段集合，保存在对象中供后续校验、查询或展示
     * @param rowChangeCounts 行变更{@code counts}，保存在对象中供后续校验、查询或展示
     */
    public record NodeComparison(
            String nodeCode,
            String nodeKind,
            String oldRelationName,
            String newRelationName,
            String displayName,
            String oldEntityName,
            String newEntityName,
            String comparability,
            List<FormSectionComparison> formSections,
            RowChangeCounts rowChangeCounts) {

        /**
         * 初始化节点比较，保存构造参数供后续方法使用。
         *
         * @param nodeCode 节点编码，后续用于初始化节点比较时定位或关联目标
         * @param nodeKind 节点类型，保存在对象中供后续校验、查询或展示
         * @param oldRelationName 旧关系名称，后续用于初始化节点比较时匹配或展示
         * @param newRelationName 新关系名称，后续用于初始化节点比较时匹配或展示
         * @param displayName 用户可见名称，供界面和日志展示
         * @param oldEntityName 旧实体名称，后续用于初始化节点比较时匹配或展示
         * @param newEntityName 新实体名称，后续用于初始化节点比较时匹配或展示
         * @param comparability {@code comparability}，保存在对象中供后续校验、查询或展示
         * @param formSections 表单区段集合，保存在对象中供后续校验、查询或展示
         * @param rowChangeCounts 行变更{@code counts}，保存在对象中供后续校验、查询或展示
         */
        public NodeComparison {
            formSections = formSections == null
                    ? List.of() : List.copyOf(formSections);
        }
    }

    /**
     * 封装表单区段比较的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param sectionCode 区段编码，后续用于处理表单区段比较时定位或关联目标
     * @param sectionName 区段名称，后续用于处理表单区段比较时匹配或展示
     * @param fields 字段集合，后续逐项校验、转换或持久化
     */
    public record FormSectionComparison(
            String sectionCode,
            String sectionName,
            List<FieldComparison> fields) {

        /**
         * 初始化表单区段比较，保存构造参数供后续方法使用。
         *
         * @param sectionCode 区段编码，后续用于初始化表单区段比较时定位或关联目标
         * @param sectionName 区段名称，后续用于初始化表单区段比较时匹配或展示
         * @param fields 字段集合，后续逐项校验、转换或持久化
         */
        public FormSectionComparison {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    /**
     * 封装字段比较的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param fieldCode 字段编码，后续用于处理字段比较时定位或关联目标
     * @param oldFieldName 旧字段名称，后续用于处理字段比较时匹配或展示
     * @param newFieldName 新字段名称，后续用于处理字段比较时匹配或展示
     * @param displayLabel 展示标签，后续用于处理字段比较时匹配或展示
     * @param oldFieldType 旧字段类型标识，决定后续字段比较采用的处理分支
     * @param newFieldType 新字段类型标识，决定后续字段比较采用的处理分支
     * @param oldValue 旧值，保存在对象中供后续校验、查询或展示
     * @param newValue 新值，保存在对象中供后续校验、查询或展示
     * @param changeType 变更类型标识，决定后续字段比较采用的处理分支
     * @param displayChanged 展示已变更，保存在对象中供后续校验、查询或展示
     * @param schemaChanges 结构变更集合，保存在对象中供后续校验、查询或展示
     */
    public record FieldComparison(
            String fieldCode,
            String oldFieldName,
            String newFieldName,
            String displayLabel,
            String oldFieldType,
            String newFieldType,
            FrozenValue oldValue,
            FrozenValue newValue,
            String changeType,
            boolean displayChanged,
            List<String> schemaChanges) {

        /**
         * 初始化字段比较，保存构造参数供后续方法使用。
         *
         * @param fieldCode 字段编码，后续用于初始化字段比较时定位或关联目标
         * @param oldFieldName 旧字段名称，后续用于初始化字段比较时匹配或展示
         * @param newFieldName 新字段名称，后续用于初始化字段比较时匹配或展示
         * @param displayLabel 展示标签，后续用于初始化字段比较时匹配或展示
         * @param oldFieldType 旧字段类型标识，决定后续字段比较采用的处理分支
         * @param newFieldType 新字段类型标识，决定后续字段比较采用的处理分支
         * @param oldValue 旧值，保存在对象中供后续校验、查询或展示
         * @param newValue 新值，保存在对象中供后续校验、查询或展示
         * @param changeType 变更类型标识，决定后续字段比较采用的处理分支
         * @param displayChanged 展示已变更，保存在对象中供后续校验、查询或展示
         * @param schemaChanges 结构变更集合，保存在对象中供后续校验、查询或展示
         */
        public FieldComparison {
            schemaChanges = schemaChanges == null
                    ? new ArrayList<>() : List.copyOf(schemaChanges);
        }
    }

    /**
     * 封装行变更{@code counts}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param added {@code added}，保存在对象中供后续校验、查询或展示
     * @param removed {@code removed}，保存在对象中供后续校验、查询或展示
     * @param modified {@code modified}，保存在对象中供后续校验、查询或展示
     * @param moved {@code moved}，保存在对象中供后续校验、查询或展示
     * @param unchanged {@code unchanged}，保存在对象中供后续校验、查询或展示
     * @param total 总数，保存在对象中供后续校验、查询或展示
     */
    public record RowChangeCounts(
            int added,
            int removed,
            int modified,
            int moved,
            int unchanged,
            int total) {
    }

    /**
     * 封装行比较的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param oldRecordTitle 旧记录{@code title}，后续用于处理行比较时匹配或展示
     * @param newRecordTitle 新记录{@code title}，后续用于处理行比较时匹配或展示
     * @param changeType 变更类型标识，决定后续行比较采用的处理分支
     * @param moved {@code moved}，保存在对象中供后续校验、查询或展示
     * @param oldOrder 旧顺序，保存在对象中供后续校验、查询或展示
     * @param newOrder 新顺序，保存在对象中供后续校验、查询或展示
     * @param formSections 表单区段集合，保存在对象中供后续校验、查询或展示
     */
    public record RowComparison(
            String recordId,
            String oldRecordTitle,
            String newRecordTitle,
            String changeType,
            boolean moved,
            Integer oldOrder,
            Integer newOrder,
            List<FormSectionComparison> formSections) {

        /**
         * 初始化行比较，保存构造参数供后续方法使用。
         *
         * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
         * @param oldRecordTitle 旧记录{@code title}，后续用于初始化行比较时匹配或展示
         * @param newRecordTitle 新记录{@code title}，后续用于初始化行比较时匹配或展示
         * @param changeType 变更类型标识，决定后续行比较采用的处理分支
         * @param moved {@code moved}，保存在对象中供后续校验、查询或展示
         * @param oldOrder 旧顺序，保存在对象中供后续校验、查询或展示
         * @param newOrder 新顺序，保存在对象中供后续校验、查询或展示
         * @param formSections 表单区段集合，保存在对象中供后续校验、查询或展示
         */
        public RowComparison {
            formSections = formSections == null
                    ? List.of() : List.copyOf(formSections);
        }
    }

    /**
     * 封装行比较分页的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param nodeCode 节点编码，后续用于处理行比较分页时定位或关联目标
     * @param relationName 关系名称，后续用于处理行比较分页时匹配或展示
     * @param records 记录集合，保存在对象中供后续校验、查询或展示
     * @param total 总数，保存在对象中供后续校验、查询或展示
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param counts {@code counts}，保存在对象中供后续校验、查询或展示
     */
    public record RowComparisonPage(
            String nodeCode,
            String relationName,
            List<RowComparison> records,
            long total,
            long pageNum,
            long pageSize,
            RowChangeCounts counts) {

        /**
         * 初始化行比较分页，保存构造参数供后续方法使用。
         *
         * @param nodeCode 节点编码，后续用于初始化行比较分页时定位或关联目标
         * @param relationName 关系名称，后续用于初始化行比较分页时匹配或展示
         * @param records 记录集合，保存在对象中供后续校验、查询或展示
         * @param total 总数，保存在对象中供后续校验、查询或展示
         * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
         * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
         * @param counts {@code counts}，保存在对象中供后续校验、查询或展示
         */
        public RowComparisonPage {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }

    /**
     * 封装快照行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param recordTitle 记录{@code title}，后续用于处理快照行时匹配或展示
     * @param rowOrder 行顺序，保存在对象中供后续校验、查询或展示
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     */
    public record SnapshotRow(
            String recordId,
            String recordTitle,
            Integer rowOrder,
            Map<String, FrozenValue> values) {
    }

    /**
     * 封装快照行分页的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param nodeCode 节点编码，后续用于处理快照行分页时定位或关联目标
     * @param relationCode 关系编码，后续用于处理快照行分页时定位或关联目标
     * @param relationName 关系名称，后续用于处理快照行分页时匹配或展示
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityName 实体名称，后续用于处理快照行分页时匹配或展示
     * @param presentation 展示，保存在对象中供后续校验、查询或展示
     * @param records 记录集合，保存在对象中供后续校验、查询或展示
     * @param total 总数，保存在对象中供后续校验、查询或展示
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     */
    public record SnapshotRowPage(
            String nodeCode,
            String relationCode,
            String relationName,
            String entityCode,
            String entityName,
            Map<String, Object> presentation,
            List<SnapshotRow> records,
            long total,
            long pageNum,
            long pageSize) {

        /**
         * 初始化快照行分页，保存构造参数供后续方法使用。
         *
         * @param nodeCode 节点编码，后续用于初始化快照行分页时定位或关联目标
         * @param relationCode 关系编码，后续用于初始化快照行分页时定位或关联目标
         * @param relationName 关系名称，后续用于初始化快照行分页时匹配或展示
         * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
         * @param entityName 实体名称，后续用于初始化快照行分页时匹配或展示
         * @param presentation 展示，保存在对象中供后续校验、查询或展示
         * @param records 记录集合，保存在对象中供后续校验、查询或展示
         * @param total 总数，保存在对象中供后续校验、查询或展示
         * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
         * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
         */
        public SnapshotRowPage {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }
}
