package com.workflow.entity.version.application.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 类型化的表单式版本比较契约。 */
public record RecordVersionComparisonV2(
        int contractVersion,
        String compatibilityMode,
        VersionSide fromVersion,
        VersionSide toVersion,
        ComparisonSummary summary,
        EntityVersionConfiguration.DiffPolicy diffPolicy,
        List<NodeComparison> nodes,
        List<String> warnings) {

    public RecordVersionComparisonV2 {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

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

        public NodeComparison {
            formSections = formSections == null
                    ? List.of() : List.copyOf(formSections);
        }
    }

    public record FormSectionComparison(
            String sectionCode,
            String sectionName,
            List<FieldComparison> fields) {

        public FormSectionComparison {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

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

        public FieldComparison {
            schemaChanges = schemaChanges == null
                    ? new ArrayList<>() : List.copyOf(schemaChanges);
        }
    }

    public record RowChangeCounts(
            int added,
            int removed,
            int modified,
            int moved,
            int unchanged,
            int total) {
    }

    public record RowComparison(
            String recordId,
            String oldRecordTitle,
            String newRecordTitle,
            String changeType,
            boolean moved,
            Integer oldOrder,
            Integer newOrder,
            List<FormSectionComparison> formSections) {

        public RowComparison {
            formSections = formSections == null
                    ? List.of() : List.copyOf(formSections);
        }
    }

    public record RowComparisonPage(
            String nodeCode,
            String relationName,
            List<RowComparison> records,
            long total,
            long pageNum,
            long pageSize,
            RowChangeCounts counts) {

        public RowComparisonPage {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }

    public record SnapshotRow(
            String recordId,
            String recordTitle,
            Integer rowOrder,
            Map<String, FrozenValue> values) {
    }

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

        public SnapshotRowPage {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }
}
