package com.workflow.entity.list.application;

import com.workflow.core.result.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityListPageResultNormalizerTest {

    private final EntityListPageResultNormalizer normalizer =
            new EntityListPageResultNormalizer();

    @Test
    void normalizesAliasFieldsToPlatformPageContract() {
        PageResult<?> result = normalizer.normalize(
                Map.of(
                        "rows", List.of(Map.of("id", "1")),
                        "total", 12,
                        "current", 2,
                        "size", 5),
                1,
                20);

        assertEquals(1, result.getRecords().size());
        assertEquals(12, result.getTotal());
        assertEquals(2, result.getPageNum());
        assertEquals(5, result.getPageSize());
    }

    @Test
    void slicesLegacyArrayResultsUsingRequestedPage() {
        PageResult<?> result = normalizer.normalize(
                List.of("a", "b", "c"),
                2,
                2);

        assertEquals(List.of("c"), result.getRecords());
        assertEquals(3, result.getTotal());
        assertEquals(2, result.getPageNum());
        assertEquals(2, result.getPageSize());
    }

    @Test
    void rejectsObjectWithoutListData() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalize(
                        Map.of("total", 1),
                        1,
                        20));

        assertEquals(
                "列表查询结果缺少 records、list 或 rows 数组",
                exception.getMessage());
    }
}
