package com.workflow.service.permission;

import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.permission.api.request.EntityListScopeInventoryBatchConfirmRequest;
import com.workflow.entity.permission.api.request.EntityListScopeInventoryConfirmItem;
import com.workflow.entity.permission.application.EntityListScopeInventoryService;
import com.workflow.entity.permission.application.EntityListScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class EntityListScopeInventoryServiceTest {

    @Test
    void invalidSecondItemPreventsAnyDatabaseWriteOrLock() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityListConfigMapper listConfigMapper = mock(EntityListConfigMapper.class);
        EntityListScopeService scopeService = mock(EntityListScopeService.class);
        EntityListScopeInventoryService service =
                new EntityListScopeInventoryService(jdbcTemplate, listConfigMapper, scopeService);
        EntityListScopeInventoryBatchConfirmRequest request = request(
                item("list-1", "owner-1", "负责人甲", "DENY_ALL", null),
                item("list-2", "", "负责人乙", "PERSONAL", null));

        assertThrows(IllegalArgumentException.class, () -> service.batchConfirm(request));
        verifyNoInteractions(jdbcTemplate, listConfigMapper, scopeService);
    }

    @Test
    void explicitAllPermissionIsCheckedBeforeDatabaseLocking() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityListConfigMapper listConfigMapper = mock(EntityListConfigMapper.class);
        EntityListScopeService scopeService = mock(EntityListScopeService.class);
        EntityListScopeInventoryService service =
                new EntityListScopeInventoryService(jdbcTemplate, listConfigMapper, scopeService);
        EntityListScopeInventoryBatchConfirmRequest request = request(
                item("list-1", "owner-1", "负责人甲", "EXPLICIT_ALL", "业务确认需要全量查看"));
        doThrow(new IllegalStateException("permission denied"))
                .when(scopeService).requireExplicitAllPermission();

        assertThrows(IllegalStateException.class, () -> service.batchConfirm(request));
        verify(scopeService).requireExplicitAllPermission();
        verifyNoInteractions(jdbcTemplate, listConfigMapper);
    }

    private static EntityListScopeInventoryBatchConfirmRequest request(
            EntityListScopeInventoryConfirmItem... items) {
        EntityListScopeInventoryBatchConfirmRequest request =
                new EntityListScopeInventoryBatchConfirmRequest();
        request.setItems(List.of(items));
        return request;
    }

    private static EntityListScopeInventoryConfirmItem item(
            String listId, String ownerId, String ownerName,
            String selectedPolicy, String reason) {
        EntityListScopeInventoryConfirmItem item = new EntityListScopeInventoryConfirmItem();
        item.setListId(listId);
        item.setOwnerId(ownerId);
        item.setOwnerName(ownerName);
        item.setSelectedPolicy(selectedPolicy);
        item.setReason(reason);
        return item;
    }
}
