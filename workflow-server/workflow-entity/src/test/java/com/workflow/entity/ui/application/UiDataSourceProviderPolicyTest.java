package com.workflow.entity.ui.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class UiDataSourceProviderPolicyTest {

    @Test
    void requiresProviderCodeForProviderBackedSources() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UiDataSourceProviderPolicy.validate(
                        "REGISTERED_PROVIDER",
                        " ",
                        Map.of()));
    }
}
