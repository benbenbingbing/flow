package com.workflow.entity.version.application.model;

import java.util.List;

/** V2 固化范围预览，不写入任何版本数据。 */
public record EntityVersionScopePreview(
        boolean valid,
        Integer totalRows,
        Long estimatedBytes,
        boolean exceedsLimit,
        List<DatasetPreview> datasets,
        List<String> warnings) {

    public record DatasetPreview(
            String nodeCode,
            String relationCode,
            String relationName,
            String entityCode,
            String entityName,
            Integer rowCount,
            Integer maxRows,
            boolean exceedsLimit) {
    }
}
