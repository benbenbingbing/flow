package com.workflow.entity.form.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 表单提交前多步骤映射结果的合并规则测试。 */
class PublishedFormSubmissionOutputMergeTest {

    /** 同一父对象下的不同叶子输出必须全部保留，只有相同叶子允许后值覆盖。 */
    @Test
    void recursivelyMergesSiblingOutputPaths() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("owner", Map.of(
                "name", "旧名称",
                "department", Map.of("name", "研发部")));

        PublishedFormSubmissionService.mergeMappedOutput(
                record,
                Map.of("owner", Map.of(
                        "name", "张三",
                        "age", 28,
                        "department", Map.of("code", "RD"))));

        assertEquals(
                Map.of(
                        "name", "张三",
                        "age", 28,
                        "department", Map.of(
                                "name", "研发部",
                                "code", "RD")),
                record.get("owner"));
    }
}
