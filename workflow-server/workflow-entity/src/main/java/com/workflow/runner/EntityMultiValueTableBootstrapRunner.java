package com.workflow.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.EntityDefinition;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.service.DynamicTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 为存量动态实体补齐每实体多值表。
 */
@Slf4j
@Component
@Order(15)
@RequiredArgsConstructor
public class EntityMultiValueTableBootstrapRunner implements ApplicationRunner {

    private final EntityDefinitionMapper definitionMapper;
    private final DynamicTableService dynamicTableService;

    /**
     * 应用启动入口：遍历已发布的动态实体，在主表存在时补齐每实体多值表。
     *
     * @param args 启动参数（本 Runner 未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        for (EntityDefinition definition : definitionMapper.selectList(
                new LambdaQueryWrapper<EntityDefinition>()
                        .eq(EntityDefinition::getStorageMode, EntityDefinition.StorageMode.DYNAMIC)
                        .eq(EntityDefinition::getStatus, EntityDefinition.Status.PUBLISHED))) {
            try {
                // 仅在主表已存在时补齐多值表，避免对未落库实体报错
                if (dynamicTableService.tableExists(definition.getEntityCode())) {
                    dynamicTableService.ensureEntityMultiValueTable(definition.getEntityCode());
                }
            } catch (Exception exception) {
                log.error("补齐实体多值表失败: entityCode={}", definition.getEntityCode(), exception);
            }
        }
    }
}
