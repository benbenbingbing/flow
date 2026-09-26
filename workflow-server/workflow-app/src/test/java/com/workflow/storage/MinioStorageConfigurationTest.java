package com.workflow.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.workflow.storage.application.FileStorageFactory;
import com.workflow.storage.application.port.FileStorageStrategy;
import com.workflow.storage.infrastructure.config.FileStorageProperties;
import com.workflow.storage.infrastructure.config.ProductionStorageConfigurationGuard;
import com.workflow.storage.infrastructure.minio.MinioFileStorageStrategy;
import com.workflow.storage.infrastructure.s3.S3FileStorageStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MinioStorageConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(FileStorageProperties.class, FileStorageFactory.class,
                    S3FileStorageStrategy.class, MinioFileStorageStrategy.class,
                    ProductionStorageConfigurationGuard.class)
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"));

    @Test
    void minioBindsIndependentPropertiesAndIsSelectedInProduction() {
        runner.withPropertyValues("file.storage.type=minio",
                "file.storage.minio.endpoint=http://localhost:9000",
                "file.storage.minio.bucket=flow-files",
                "file.storage.minio.access-key=minio-test-access",
                "file.storage.minio.secret-key=minio-test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(FileStorageStrategy.class)
                            .hasSingleBean(ProductionStorageConfigurationGuard.class);
                    FileStorageStrategy strategy = context.getBean(FileStorageFactory.class).getStrategy();
                    assertThat(strategy).isExactlyInstanceOf(MinioFileStorageStrategy.class);
                    assertThat(strategy.getStorageType()).isEqualTo("minio");
                    assertThat(strategy.getAccessUrl("image.png")).isEqualTo("s3://flow-files/image.png");
                });
    }

    @Test
    void existingS3ConfigurationDoesNotRequireMinioProperties() {
        runner.withPropertyValues("file.storage.type=s3", "file.storage.s3.bucket=existing-files")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(FileStorageStrategy.class)
                            .doesNotHaveBean(MinioFileStorageStrategy.class);
                    assertThat(context.getBean(FileStorageFactory.class).getStrategy())
                            .isExactlyInstanceOf(S3FileStorageStrategy.class);
                });
    }
}
