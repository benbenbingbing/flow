package com.workflow.entity.excel.runtime;

import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.application.UiEventOriginContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;

class UiEventOriginContextTest {
    @Test void nestedEventsRestoreOuterOriginAndCleanThreadAfterFailure() {
        var outer = new UiEventExecuteRequest(); outer.setTargetKey("outer");
        var inner = new UiEventExecuteRequest(); inner.setTargetKey("inner");
        var chain = new UiEventBindingService.ResolvedEventChain(List.of(), "release", 1, "e", "employee", null, Map.of());
        assertThat(UiEventOriginContext.current()).isNull();
        try (var ignored = UiEventOriginContext.open(outer, chain)) {
            assertThat(UiEventOriginContext.current().request()).isSameAs(outer);
            assertThatThrownBy(() -> {
                try (var nested = UiEventOriginContext.open(inner, chain)) {
                    assertThat(UiEventOriginContext.current().request()).isSameAs(inner);
                    throw new IllegalArgumentException("failure");
                }
            }).isInstanceOf(IllegalArgumentException.class);
            assertThat(UiEventOriginContext.current().request()).isSameAs(outer);
        }
        assertThat(UiEventOriginContext.current()).isNull();
    }
    @Test void originIsNotInheritedByUnrelatedAsyncThread() {
        try (var ignored = UiEventOriginContext.open(new UiEventExecuteRequest(), null)) {
            assertThat(CompletableFuture.supplyAsync(UiEventOriginContext::current).join()).isNull();
        }
    }
}
