package com.workflow.entity.permission.bootstrap;

import com.workflow.contracts.bootstrap.BootstrapJobCoordinator;
import com.workflow.entity.permission.application.EntityPermissionCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时同步实体标准权限和历史按钮配置。
 */
@Component
@ConditionalOnProperty(
        name = "workflow.bootstrap.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Order(30)
@RequiredArgsConstructor
public class EntityPermissionBootstrapRunner implements ApplicationRunner {

    private final EntityPermissionCatalogService catalogService;
    private final BootstrapJobCoordinator bootstrapJobCoordinator;

    /**
     * 应用启动入口：同步全部实体的标准权限目录与历史按钮配置。
     *
     * @param args 启动参数（本 Runner 未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        bootstrapJobCoordinator.executeOnce(
                "entity-permission-catalog",
                1,
                () -> {
                    catalogService.synchronizeAll();
                    return true;
                });
    }
}
