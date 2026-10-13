package com.workflow.process.form.infrastructure.adapter;

import com.workflow.contracts.entity.form.port.EntityNewDataFormRuntimePort.ResolvedForm;
import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.process.form.application.EntityFormResolveService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Embed 内部配置坐标由实体服务恢复，不再依赖公共表单 DTO 的版本占位字段。 */
class EntityNewDataFormRuntimeAdapterTest {
    @Test void resolvesCurrentIdentityFromStableNativeFormId() {
        var nativeService = mock(EntityFormResolveService.class);
        var releases = mock(UiConfigReleaseService.class);
        when(nativeService.resolveFormForNewData("expense")).thenReturn(Map.of("id", "form-first", "configHash", "graph-hash"));
        when(releases.resolveRuntimeFormRelease("form-first")).thenReturn(new ResolvedEntityFormRelease(
                new EntityForm(), "current-hash", 1, false, "current-hash", "current-hash", null, UiRuntimePurpose.STANDALONE));
        var adapter = new EntityNewDataFormRuntimeAdapter(nativeService, releases);
        assertEquals(new ResolvedForm("form-first", "current-hash", 1), adapter.resolveForNewData("expense").orElseThrow());
    }

    @Test void failsClosedWhenNativeResultHasNoStableIdentity() {
        var nativeService = mock(EntityFormResolveService.class);
        var releases = mock(UiConfigReleaseService.class);
        when(nativeService.resolveFormForNewData("expense")).thenReturn(Map.of("configHash", "hash"));
        assertThrows(IllegalStateException.class, () -> new EntityNewDataFormRuntimeAdapter(nativeService, releases).resolveForNewData("expense"));
        verifyNoInteractions(releases);
    }
}
