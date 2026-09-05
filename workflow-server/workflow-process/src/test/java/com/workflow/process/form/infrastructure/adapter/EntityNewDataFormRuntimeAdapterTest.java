package com.workflow.process.form.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.workflow.contracts.entity.form.port.EntityNewDataFormRuntimePort.ResolvedForm;
import com.workflow.process.form.application.EntityFormResolveService;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 原生新增表单解析跨模块适配测试。 */
class EntityNewDataFormRuntimeAdapterTest {

    @Test
    void exposesExactFirstNodeReleaseResolvedByNativeFlowService() {
        EntityFormResolveService nativeService = mock(
                EntityFormResolveService.class);
        when(nativeService.resolveFormForNewData("expense"))
                .thenReturn(Map.of(
                        "id", "form-first",
                        "runtimeReleaseId", "release-2",
                        "runtimeReleaseVersion", 2));
        EntityNewDataFormRuntimeAdapter adapter =
                new EntityNewDataFormRuntimeAdapter(nativeService);

        assertEquals(
                new ResolvedForm("form-first", "release-2", 2),
                adapter.resolveForNewData("expense").orElseThrow());
    }

    @Test
    void failsClosedWhenNativeResultHasNoPublishedReleaseCoordinates() {
        EntityFormResolveService nativeService = mock(
                EntityFormResolveService.class);
        when(nativeService.resolveFormForNewData("expense"))
                .thenReturn(Map.of("id", "form-first"));

        assertThrows(
                IllegalStateException.class,
                () -> new EntityNewDataFormRuntimeAdapter(nativeService)
                        .resolveForNewData("expense"));
    }
}
