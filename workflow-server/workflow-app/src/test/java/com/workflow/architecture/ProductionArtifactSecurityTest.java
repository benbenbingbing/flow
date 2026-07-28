package com.workflow.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionArtifactSecurityTest {

    @Test
    void defaultApplicationGraphExcludesDevtoolsAndGroovy() throws Exception {
        String appPom = Files.readString(Path.of("pom.xml"));
        String processPom = Files.readString(
                Path.of("../workflow-process/pom.xml"));

        int devtools = appPom.indexOf("<artifactId>workflow-devtools</artifactId>");
        assertTrue(devtools >= 0);
        String devtoolsDependency = appPom.substring(
                appPom.lastIndexOf("<dependency>", devtools),
                appPom.indexOf("</dependency>", devtools));
        assertTrue(devtoolsDependency.contains("<scope>test</scope>"));
        assertFalse(processPom.contains("org.codehaus.groovy"));
        assertFalse(processPom.contains("<artifactId>groovy</artifactId>"));
    }

    @Test
    void devtoolsProfileHasNoScriptExecutionEndpoint() {
        assertFalse(Files.exists(Path.of(
                "../workflow-devtools/src/main/java/com/workflow/devtools/"
                        + "script/api/web/ScriptTestController.java")));
        assertFalse(Files.exists(Path.of(
                "../workflow-devtools/src/main/java/com/workflow/devtools/"
                        + "script/application/ScriptTestService.java")));
    }

    @Test
    void datasourceDoesNotEnableMultipleStatements() throws Exception {
        String applicationConfig = Files.readString(
                Path.of("src/main/resources/application.yml"));
        String jwtSource = Files.readString(Path.of(
                "../workflow-admin/src/main/java/com/workflow/admin/auth/"
                        + "infrastructure/JwtUtil.java"));

        assertFalse(applicationConfig.contains("allowMultiQueries=true"));
        assertTrue(applicationConfig.contains("serverTimezone=UTC"));
        assertFalse(applicationConfig.contains("zhoudawei"));
        assertTrue(applicationConfig.contains("password: ${DB_PASSWORD}"));
        assertTrue(applicationConfig.contains("secret: ${JWT_SECRET}"));
        assertTrue(applicationConfig.contains(
                "log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl"));
        assertTrue(applicationConfig.contains(
                "com.workflow: ${WORKFLOW_LOG_LEVEL:INFO}"));
        assertFalse(jwtSource.contains("${jwt.secret:"));
    }
}
