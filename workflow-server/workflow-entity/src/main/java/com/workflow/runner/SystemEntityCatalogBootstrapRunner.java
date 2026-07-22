package com.workflow.runner;

import com.workflow.service.SystemEntityCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class SystemEntityCatalogBootstrapRunner implements ApplicationRunner {

    private final SystemEntityCatalogService catalogService;

    @Override
    public void run(ApplicationArguments args) {
        int count = catalogService.synchronize();
        log.info("平台系统实体目录同步完成: tables={}", count);
    }
}
