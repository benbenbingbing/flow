package com.workflow.runner;

import com.workflow.entity.EntityDefinition;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.service.EntityRecordTeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 实体参与团队表启动补齐 Runner。
 * <p>应用启动时遍历所有非系统实体，为其补建「实体参与团队」表并在空表时回填团队数据，
 * 保障团队权限对历史动态实体的可用性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityRecordTeamBootstrapRunner implements ApplicationRunner {

    private final EntityDefinitionMapper definitionMapper;
    private final EntityRecordTeamService teamService;

    /**
     * 应用启动入口：遍历全部实体定义，对非系统实体补齐参与团队表与初始数据。
     *
     * @param args 启动参数（本 Runner 未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        for (EntityDefinition definition : definitionMapper.selectList(null)) {
            // 系统实体的团队表由其自身维护，跳过
            if (definition.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
                continue;
            }
            try {
                teamService.ensureTeamTable(definition);
                teamService.backfillIfEmpty(definition);
            } catch (RuntimeException exception) {
                log.error("补齐实体参与团队表失败: entityCode={}",
                        definition.getEntityCode(), exception);
            }
        }
    }
}
