package com.workflow.mapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperDeleteAnnotationTest {

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
