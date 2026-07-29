package com.workflow.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.workflow.storage.infrastructure.config.FileStorageProperties;
import com.workflow.storage.infrastructure.config.ProductionStorageConfigurationGuard;
import org.junit.jupiter.api.Test;

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

    @Test
    void productionAcceptsSharedS3Storage() {
        FileStorageProperties properties =
                new FileStorageProperties();
        properties.setType("s3");

        assertDoesNotThrow(
                () -> new ProductionStorageConfigurationGuard(
                        properties));
    }
}
