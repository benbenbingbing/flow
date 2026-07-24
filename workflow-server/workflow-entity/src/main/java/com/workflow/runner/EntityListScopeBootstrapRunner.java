package com.workflow.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.EntityDefinition;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.service.permission.EntityListScopeManualReviewRequiredException;
import com.workflow.service.permission.EntityListScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 实体列表数据范围启动初始化 Runner。
 * <p>应用启动时按创建时间遍历非系统实体，为其初始化默认数据范围策略并发布，
 * 保证升级后历史实体具备可用的列表访问范围（默认拒绝访问，需人工配置放行）。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class EntityListScopeBootstrapRunner implements ApplicationRunner {

    private final EntityDefinitionMapper definitionMapper;
    private final EntityListScopeService scopeService;

    /**
     * 应用启动入口：逐实体初始化默认数据范围并发布，人工复核需求仅告警不中断。
     *
     * @param args 启动参数（本 Runner 未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        for (EntityDefinition entity : definitionMapper.selectList(
                new LambdaQueryWrapper<EntityDefinition>()
                        .orderByAsc(EntityDefinition::getCreatedAt))) {
            // 系统实体数据范围由其自身管理，跳过
            if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
                continue;
            }
            try {
                scopeService.ensureDefaultAndRelease(entity.getEntityCode());
            } catch (EntityListScopeManualReviewRequiredException exception) {
                log.warn(
                        "实体列表数据范围等待人工复核，保持默认拒绝访问: entityCode={}, reason={}",
                        entity.getEntityCode(),
                        exception.getMessage());
            } catch (Exception exception) {
                log.error("初始化实体列表数据范围失败，实体将默认拒绝访问: entityCode={}",
                        entity.getEntityCode(), exception);
            }
        }
    }
}
