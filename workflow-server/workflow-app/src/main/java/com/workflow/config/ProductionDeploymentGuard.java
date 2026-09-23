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

    /**
     * 初始化{@code production}{@code deployment}保护，保存构造参数供后续方法使用。
     *
     * @param flywayEnabled {@code flyway}启用，保存在对象中供后续校验、查询或展示
     * @param flowableSchemaUpdate Flowable结构更新，后续用于判断有效期或展示该事件的发生时间
     * @param deploymentRole {@code deployment}角色，保存在对象中供后续校验、查询或展示
     * @param bootstrapEnabled 初始化启用，保存在对象中供后续校验、查询或展示
     * @param exitAfterBootstrap {@code exit}之后初始化，保存在对象中供后续校验、查询或展示
     * @param schedulingEnabled {@code scheduling}启用，保存在对象中供后续校验、查询或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 校验并获取{@code production}{@code deployment}保护；不满足约束时阻止后续处理。
     *
     * @param valid 有效，后续用于校验并获取{@code production}{@code deployment}保护时定位或关联目标
     * @param message 消息，作为 {@code IllegalStateException} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private void require(
            boolean valid,
            String message) {
        if (!valid) {
            throw new IllegalStateException(message);
        }
    }
}
