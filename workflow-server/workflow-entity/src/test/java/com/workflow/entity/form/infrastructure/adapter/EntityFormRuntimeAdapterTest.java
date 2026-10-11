package com.workflow.entity.form.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.form.model.EntityFormBinding;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 新数据入口只检查当前表单是否已发布，旧流程 pin 不得要求热修复放行。 */
class EntityFormRuntimeAdapterTest {
    private final UiConfigReleaseService releases = mock(UiConfigReleaseService.class);
    private final EntityFormRuntimeAdapter adapter = new EntityFormRuntimeAdapter(
            mock(EntityDefinitionMapper.class), mock(EntityFormMapper.class), releases,
            mock(UiReleaseResolutionTokenService.class), new ObjectMapper());

    @Test
    void newDataAcceptsOldProcessCoordinatesWhenCurrentFormExists() {
        var current = new UiConfigRelease(); current.setContentHash("current");
        when(releases.active("FORM", "form")).thenReturn(current);
        assertDoesNotThrow(() -> adapter.requireCurrentBindingForNewData(
                new EntityFormBinding("task", "form", "deleted-old-version", 9), "history"));
        verify(releases).active("FORM", "form");
        verifyNoMoreInteractions(releases);
    }

    @Test
    void unpublishedFormRemainsBlocked() {
        var failure = assertThrows(BusinessConflictException.class, () -> adapter.requireCurrentBindingForNewData(
                new EntityFormBinding("task", "form", "old-version", 9), "history"));
        assertTrue(failure.getMessage().contains("尚未发布"));
    }
}
