package com.workflow.service.entity;

import com.workflow.common.BusinessConflictException;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.entity.UiConfigRelease;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.service.UiConfigReleaseService;
import com.workflow.service.UiReleaseResolutionTokenService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityFormRuntimeServiceTest {

    @Test
    void newDataAcceptsBindingPinnedToCurrentActiveRelease() {
        UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
        EntityFormRuntimeService service = new EntityFormRuntimeService(
                releaseService,
                mock(EntityFormMapper.class),
                mock(UiReleaseResolutionTokenService.class));
        ProcessNodeForm binding = binding("release-3", 3);
        when(releaseService.active(UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(release("release-3", 3));

        assertDoesNotThrow(() -> service.requireCurrentBindingForNewData(binding));
    }

    @Test
    void newDataRejectsBindingPinnedToOlderRelease() {
        UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
        EntityFormRuntimeService service = new EntityFormRuntimeService(
                releaseService,
                mock(EntityFormMapper.class),
                mock(UiReleaseResolutionTokenService.class));
        ProcessNodeForm binding = binding("release-2", 2);
        when(releaseService.active(UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(release("release-3", 3));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.requireCurrentBindingForNewData(binding));

        assertEquals("PROCESS_FORM_RELEASE_STALE", exception.getErrorCode());
        assertEquals(
                "流程节点表单已发布新版本，请重新发布流程后再新增数据",
                exception.getMessage());
    }

    @Test
    void newDataAcceptsLegacyBindingWithoutPinnedRelease() {
        UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
        EntityFormRuntimeService service = new EntityFormRuntimeService(
                releaseService,
                mock(EntityFormMapper.class),
                mock(UiReleaseResolutionTokenService.class));
        ProcessNodeForm binding = new ProcessNodeForm();
        binding.setFormId("form-1");
        when(releaseService.active(UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(release("release-3", 3));

        assertDoesNotThrow(() -> service.requireCurrentBindingForNewData(binding));
    }

    @Test
    void newDataAcceptsApprovedHotfixForPinnedRelease() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        EntityFormRuntimeService service = new EntityFormRuntimeService(
                releaseService,
                mock(EntityFormMapper.class),
                mock(UiReleaseResolutionTokenService.class));
        ProcessNodeForm binding = binding("release-2", 2);
        when(releaseService.active(
                UiConfigReleaseService.FORM,
                "form-1"))
                .thenReturn(release("hotfix-3", 3));
        when(releaseService.isApprovedHotfix(
                "form-1",
                "release-2",
                2,
                "history-1",
                "hotfix-3"))
                .thenReturn(true);

        assertDoesNotThrow(() ->
                service.requireCurrentBindingForNewData(
                        binding,
                        "history-1"));
    }

    private ProcessNodeForm binding(String releaseId, int version) {
        ProcessNodeForm binding = new ProcessNodeForm();
        binding.setFormId("form-1");
        binding.setFormReleaseId(releaseId);
        binding.setFormReleaseVersion(version);
        return binding;
    }

    private UiConfigRelease release(String releaseId, int version) {
        UiConfigRelease release = new UiConfigRelease();
        release.setId(releaseId);
        release.setVersion(version);
        return release;
    }
}
