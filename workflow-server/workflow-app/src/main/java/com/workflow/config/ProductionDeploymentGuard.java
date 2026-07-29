package com.workflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Enforces separation between schema, bootstrap, and serving workloads.
 */
@Component
@Profile("production")
public class ProductionDeploymentGuard {

    public ProductionDeploymentGuard(
            @Value("${spring.flyway.enabled:true}")
            boolean flywayEnabled,
            @Value("${flowable.database-schema-update:true}")
            String flowableSchemaUpdate,
            @Value("${workflow.deployment.role:server}")
            String deploymentRole,
            @Value("${workflow.bootstrap.enabled:true}")
            boolean bootstrapEnabled,
            @Value("${workflow.bootstrap.exit-on-complete:false}")
            boolean exitAfterBootstrap,
            @Value("${workflow.scheduling.enabled:true}")
            boolean schedulingEnabled) {
        if (flywayEnabled
                || !"false".equalsIgnoreCase(
                        flowableSchemaUpdate)) {
            throw new IllegalStateException(
                    "Production application workloads cannot "
                            + "run database schema migrations");
        }
        if ("server".equals(deploymentRole)) {
            require(
                    !bootstrapEnabled
                            && !exitAfterBootstrap
                            && schedulingEnabled,
                    "Production server Pods must disable bootstrap "
                            + "and enable background scheduling");
            return;
        }
        if ("bootstrap".equals(deploymentRole)) {
            require(
                    bootstrapEnabled
                            && exitAfterBootstrap
                            && !schedulingEnabled,
                    "Production bootstrap Jobs must run bootstrap "
                            + "once, exit, and disable scheduling");
            return;
        }
        throw new IllegalStateException(
                "Unsupported production deployment role: "
                        + deploymentRole);
    }

    private void require(
            boolean valid,
            String message) {
        if (!valid) {
            throw new IllegalStateException(message);
        }
    }
}
