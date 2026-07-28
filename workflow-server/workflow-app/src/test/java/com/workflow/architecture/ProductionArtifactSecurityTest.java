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
        assertTrue(applicationConfig.contains("url: ${SCHEMA_DATASOURCE_URL}"));
        assertTrue(applicationConfig.contains("user: ${SCHEMA_DB_USERNAME}"));
        assertTrue(applicationConfig.contains("password: ${SCHEMA_DB_PASSWORD}"));
        assertTrue(applicationConfig.contains("secret: ${JWT_SECRET}"));
        assertTrue(applicationConfig.contains(
                "log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl"));
        assertTrue(applicationConfig.contains(
                "com.workflow: ${WORKFLOW_LOG_LEVEL:INFO}"));
        assertFalse(jwtSource.contains("${jwt.secret:"));
    }

    @Test
    void productionLogsUseStructuredStdout() throws Exception {
        String productionConfig = Files.readString(
                Path.of("src/main/resources/application-production.yml"));
        String defaultConfig = Files.readString(
                Path.of("src/main/resources/application.yml"));

        assertTrue(productionConfig.contains("console: ecs"));
        assertFalse(defaultConfig.contains("logging.file.name"));
        assertFalse(defaultConfig.contains("name: logs/workflow-server.log"));
    }

    @Test
    void securityPatchedPlatformDependenciesArePinned() throws Exception {
        String parentPom = Files.readString(Path.of("../pom.xml"));

        assertTrue(parentPom.contains(
                "<jackson-bom.version>2.21.4</jackson-bom.version>"));
        assertTrue(parentPom.contains(
                "<netty.version>4.1.136.Final</netty.version>"));
        assertTrue(parentPom.contains(
                "<tomcat.version>10.1.55</tomcat.version>"));
    }

    @Test
    void productionComposeKeepsRuntimeAndSchemaCredentialsSeparate() throws Exception {
        String compose = Files.readString(Path.of("../../deploy/compose.prod.yml"));

        assertTrue(compose.contains("DB_USERNAME: ${DB_USERNAME:-workflow}"));
        assertTrue(compose.contains(
                "SCHEMA_DB_USERNAME: ${SCHEMA_DB_USERNAME:?SCHEMA_DB_USERNAME is required}"));
        assertTrue(compose.contains(
                "SCHEMA_DB_PASSWORD: ${SCHEMA_DB_PASSWORD:?SCHEMA_DB_PASSWORD is required}"));
        assertTrue(compose.contains(
                "FLOWABLE_SCHEMA_UPDATE: \"false\""));
        String serverSection = compose.substring(
                compose.indexOf("  server:"),
                compose.indexOf("  web:"));
        assertFalse(serverSection.contains("SCHEMA_DB_USERNAME"));
        assertFalse(serverSection.contains("SCHEMA_DB_PASSWORD"));
        assertTrue(serverSection.contains(
                "WORKFLOW_SCHEMA_PUBLISHER_MODE: queue"));
        assertTrue(compose.contains(
                "/app/workflow-db-migrator.jar"));

        String databaseUsers = Files.readString(
                Path.of("../../deploy/mysql-init/10-database-users.sh"));
        assertTrue(databaseUsers.contains(
                "GRANT SELECT, INSERT, UPDATE, DELETE"));
        assertTrue(databaseUsers.contains(
                "runtime and schema database users must differ"));
        assertFalse(databaseUsers.contains("GRANT ALL PRIVILEGES ON"
                + " \\`${MYSQL_DATABASE}\\`.* TO '${runtime_user}'"));

        String deploymentWorkflow = Files.readString(
                Path.of("../../.github/workflows/deploy.yml"));
        assertTrue(deploymentWorkflow.contains(
                "steps.server-image.outputs.digest"));
        assertTrue(deploymentWorkflow.contains(
                "helm upgrade --install"));
        assertTrue(deploymentWorkflow.contains(
                "--atomic"));
        assertFalse(deploymentWorkflow.contains(
                "docker compose"));
    }
}
