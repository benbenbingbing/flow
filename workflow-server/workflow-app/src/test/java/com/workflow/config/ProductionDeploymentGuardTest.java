package com.workflow.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProductionDeploymentGuardTest {

    @Test
    void rejectsSchemaMigrationInServerPods() {
        assertThrows(
                IllegalStateException.class,
                () -> new ProductionDeploymentGuard(
                        true,
                        "false",
                        "server",
                        false,
                        false,
                        true));
        assertThrows(
                IllegalStateException.class,
                () -> new ProductionDeploymentGuard(
                        false,
                        "true",
                        "server",
                        false,
                        false,
                        true));
    }

    @Test
    void acceptsServingWorkloadSeparation() {
        assertDoesNotThrow(
                () -> new ProductionDeploymentGuard(
                        false,
                        "false",
                        "server",
                        false,
                        false,
                        true));
    }

    @Test
    void acceptsOneShotBootstrapWorkload() {
        assertDoesNotThrow(
                () -> new ProductionDeploymentGuard(
                        false,
                        "false",
                        "bootstrap",
                        true,
                        true,
                        false));
    }

    @Test
    void rejectsBootstrapWorkloadWithSchedulers() {
        assertThrows(
                IllegalStateException.class,
                () -> new ProductionDeploymentGuard(
                        false,
                        "false",
                        "bootstrap",
                        true,
                        true,
                        true));
    }
}
