package com.workflow.runner;

import com.workflow.entity.EntityDefinition;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.service.permission.EntityListScopeManualReviewRequiredException;
import com.workflow.service.permission.EntityListScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class EntityListScopeBootstrapRunnerTest {

    @Test
    void manualReviewRequirementDoesNotAbortBootstrap() {
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        EntityListScopeService scopeService = mock(EntityListScopeService.class);
        EntityDefinition entity = new EntityDefinition();
        entity.setEntityCode("legacy_order");
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(definitionMapper.selectList(any())).thenReturn(List.of(entity));
        doThrow(new EntityListScopeManualReviewRequiredException("需要人工复核"))
                .when(scopeService)
                .ensureDefaultAndRelease("legacy_order");

        EntityListScopeBootstrapRunner runner =
                new EntityListScopeBootstrapRunner(definitionMapper, scopeService);

        assertDoesNotThrow(() -> runner.run(new DefaultApplicationArguments()));
        verify(scopeService).ensureDefaultAndRelease("legacy_order");
    }
}
