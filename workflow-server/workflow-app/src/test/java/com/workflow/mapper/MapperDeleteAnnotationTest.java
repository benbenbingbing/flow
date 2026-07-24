package com.workflow.mapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper DELETE 语句注解守护测试。
 *
 * <p>扫描全部 Mapper 接口，验证 DELETE SQL 使用 @Delete 注解而非 @Select，
 * 防止误用查询注解执行删除操作。</p>
 */
class MapperDeleteAnnotationTest {

    /**
     * 所有 Mapper 中的 DELETE 语句应使用 @Delete 注解。
     *
     * <p>遍历 mapper 目录下所有 *Mapper.java 文件，断言无文件含 @Select("DELETE ...")。</p>
     */
    @Test
    void deleteStatementsUseDeleteAnnotation() throws Exception {
        String offenders;
        Path projectRoot = Path.of("..").normalize();
        try (var paths = Files.walk(projectRoot, 6)) {
            offenders = paths
                    .filter(path -> path.toString().contains("/src/main/java/com/workflow/mapper/"))
                    .filter(path -> path.toString().endsWith("Mapper.java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("@Select(\"DELETE ");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(projectRoot::relativize)
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.joining(", "));
        }

        assertTrue(offenders.isEmpty(), "DELETE SQL must use @Delete, not @Select: " + offenders);
    }
}
