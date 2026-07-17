package com.workflow.runner;

import com.workflow.service.permission.EntityPermissionCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时同步实体标准权限和历史按钮配置。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class EntityPermissionBootstrapRunner implements ApplicationRunner {

    private final EntityPermissionCatalogService catalogService;

    @Override
    public void run(ApplicationArguments args) {
        catalogService.synchronizeAll();
    }
}
