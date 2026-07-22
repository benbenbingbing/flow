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

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class EntityListScopeBootstrapRunner implements ApplicationRunner {

    private final EntityDefinitionMapper definitionMapper;
    private final EntityListScopeService scopeService;

    @Override
    public void run(ApplicationArguments args) {
        for (EntityDefinition entity : definitionMapper.selectList(
                new LambdaQueryWrapper<EntityDefinition>()
                        .orderByAsc(EntityDefinition::getCreatedAt))) {
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
