package com.workflow.migration.application;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.permission.application.EntityPermissionCatalogService;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证回滚停用实体不会用旧实体快照覆盖流程绑定。
 */
@ExtendWith(MockitoExtension.class)
class ConfigMigrationEntityDisableConcurrencyTest {

    @Mock
    private EntityDefinitionMapper entityMapper;

    @Mock
    private EntityPermissionCatalogService permissionCatalogService;

    @InjectMocks
    private ConfigMigrationImportApplyService service;

    @Test
    void disablingNewEntityLocksItAndUpdatesOnlyStatus() throws Exception {
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setEntityCode("expense");
        entity.setProcessDefinitionId("process-1");
        when(entityMapper.findByEntityCodeForUpdate("expense"))
                .thenReturn(Optional.of(entity));

        ConfigImportItem item = new ConfigImportItem();
        item.setAssetType(ConfigMigrationAssetService.ENTITY);
        item.setBusinessKey("expense");

        Method disableNewAsset = ConfigMigrationImportApplyService.class
                .getDeclaredMethod("disableNewAsset", ConfigImportItem.class);
        disableNewAsset.setAccessible(true);
        disableNewAsset.invoke(service, item);

        verify(entityMapper).findByEntityCodeForUpdate("expense");
        verify(entityMapper).updateStatus(
                "entity-1", EntityDefinition.Status.DISABLED);
        verify(entityMapper, never()).updateById(any(EntityDefinition.class));
        verify(permissionCatalogService).disableEntityPermissions("expense");
    }
}
