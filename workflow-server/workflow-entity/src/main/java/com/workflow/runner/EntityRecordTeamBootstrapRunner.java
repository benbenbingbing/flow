package com.workflow.runner;

import com.workflow.entity.EntityDefinition;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.service.EntityRecordTeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EntityRecordTeamBootstrapRunner implements ApplicationRunner {

    private final EntityDefinitionMapper definitionMapper;
    private final EntityRecordTeamService teamService;

    @Override
    public void run(ApplicationArguments args) {
        for (EntityDefinition definition : definitionMapper.selectList(null)) {
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
