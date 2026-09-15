package com.workflow.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.workflow.storage.infrastructure.config.FileStorageProperties;
import com.workflow.storage.infrastructure.config.ProductionStorageConfigurationGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductionStorageConfigurationGuardTest {

    @Test
    void productionRejectsReplicaLocalStorage() {
        FileStorageProperties properties =
                new FileStorageProperties();

        assertThrows(
                IllegalStateException.class,
                () -> new ProductionStorageConfigurationGuard(
                        properties));
    }

    @ParameterizedTest
    @ValueSource(strings = {"s3", "minio", "MINIO"})
    void productionAcceptsSharedObjectStorage(String type) {
        FileStorageProperties properties =
                new FileStorageProperties();
        properties.setType(type);

        assertDoesNotThrow(
                () -> new ProductionStorageConfigurationGuard(
                        properties));
    }
}
