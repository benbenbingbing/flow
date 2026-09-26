package com.workflow.service.entity;

import com.workflow.process.form.application.EntityFormRuntimeService;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;

class EntityFormRuntimeServiceTest {

    /**
     * 默认表单解析应将实际发布坐标保留在运行时表单对象上。
     */
    @Test
    void defaultFormCarriesResolvedReleaseCoordinates() {
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        EntityFormRuntimeService service = new EntityFormRuntimeService(
                releaseService,
                formMapper,
                mock(UiReleaseResolutionTokenService.class));
        EntityForm defaultForm = new EntityForm();
        defaultForm.setId("form-1");
        EntityForm runtimeForm = new EntityForm();
        runtimeForm.setId("form-1");
        when(formMapper.selectDefaultByEntityId("entity-1"))
                .thenReturn(defaultForm);
        when(releaseService.resolveRuntimeFormRelease("form-1"))
                .thenReturn(new ResolvedEntityFormRelease(
                        runtimeForm,
                        "release-4",
                        4));

        EntityForm result = service.getDefaultForm("entity-1");

        assertSame(runtimeForm, result);
        assertEquals("release-4", result.getRuntimeReleaseId());
        assertEquals(4, result.getRuntimeReleaseVersion());
    }

    /** 默认表单用于活动审批时，应按实际发布坐标签发与任务绑定的令牌。 */
    @Test
    void defaultFormSignsActiveTaskReleaseContext() {
        UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
        EntityFormMapper formMapper = mock(EntityFormMapper.class);
        UiReleaseResolutionTokenService tokenService =
                mock(UiReleaseResolutionTokenService.class);
        EntityFormRuntimeService service = new EntityFormRuntimeService(
                releaseService, formMapper, tokenService);
        EntityForm defaultForm = new EntityForm();
        defaultForm.setId("form-1");
        EntityForm runtimeForm = new EntityForm();
        runtimeForm.setId("form-1");
        UiRuntimeResolutionContext context = UiRuntimeResolutionContext.activeTask(
                "history-1", "task-node-1", "task-1", "instance-1", "expense", "record-1");
        when(formMapper.selectDefaultByEntityId("entity-1"))
                .thenReturn(defaultForm);
        when(releaseService.resolveRuntimeFormRelease("form-1"))
                .thenReturn(new ResolvedEntityFormRelease(runtimeForm, "release-4", 4));
        when(tokenService.issue(
                same(context), eq("form-1"), eq("release-4"), eq(4), eq(0)))
                .thenReturn("active-task-token");

        EntityForm result = service.getDefaultForm("entity-1", context);

        assertEquals("release-4", result.getRuntimeReleaseId());
        assertEquals(4, result.getRuntimeReleaseVersion());
        assertEquals("active-task-token", result.getReleaseResolutionToken());
    }

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
