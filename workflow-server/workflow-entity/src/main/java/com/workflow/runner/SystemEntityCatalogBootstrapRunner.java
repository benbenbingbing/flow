package com.workflow.runner;

import com.workflow.service.SystemEntityCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 系统实体目录启动同步 Runner。
 * <p>应用启动时同步平台内置系统实体（如 sys_user/sys_dept 等）的目录元数据，
 * 确保系统实体的表结构变更能够被实体管理模块识别。
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class SystemEntityCatalogBootstrapRunner implements ApplicationRunner {

    /** 系统实体目录同步服务 */
    private final SystemEntityCatalogService catalogService;

    /**
     * 应用启动入口：执行系统实体目录同步，并记录本次同步覆盖的表数量。
     *
     * @param args 启动参数（本 Runner 未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        int count = catalogService.synchronize();
        log.info("平台系统实体目录同步完成: tables={}", count);
    }
}
