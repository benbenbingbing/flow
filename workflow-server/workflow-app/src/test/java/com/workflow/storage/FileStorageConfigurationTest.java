package com.workflow.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.workflow.storage.application.FileStorageFactory;
import com.workflow.contracts.storage.spi.FileStorageProvider;
import com.workflow.storage.infrastructure.config.FileStorageProperties;
import com.workflow.storage.infrastructure.local.LocalFileStorageStrategy;
import com.workflow.storage.infrastructure.minio.MinioFileStorageStrategy;
import com.workflow.storage.infrastructure.s3.S3FileStorageStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

class FileStorageConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(StoragePropertiesConfiguration.class, FileStorageFactory.class,
                    LocalFileStorageStrategy.class, S3FileStorageStrategy.class, MinioFileStorageStrategy.class)
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"));

    @Test
    void productionUsesLocalStorageByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(FileStorageProvider.class);
            assertThat(context.getBean(FileStorageFactory.class).getStrategy())
                    .isExactlyInstanceOf(LocalFileStorageStrategy.class);
        });
    }

    @Test
    void productionUsesConfiguredLocalStorageDirectory() {
        runner.withPropertyValues("file.storage.type=local", "file.storage.local.path=./production-files")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(FileStorageProvider.class);
                    assertThat(context.getBean(FileStorageFactory.class).getStrategy())
                            .isExactlyInstanceOf(LocalFileStorageStrategy.class);
                    assertThat(context.getBean(FileStorageProperties.class).getLocal().getPath())
                            .isEqualTo("./production-files");
                });
    }

    @Test
    void productionUsesRegisteredCustomStorage() {
        FileStorageProvider customStorage = mock(FileStorageProvider.class);
        when(customStorage.getStorageType()).thenReturn("custom-storage");

        runner.withPropertyValues("file.storage.type=custom-storage")
                .withBean("customStorage", FileStorageProvider.class, () -> customStorage)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(FileStorageProvider.class);
                    assertThat(context.getBean(FileStorageFactory.class).getStrategy()).isSameAs(customStorage);
                });
    }

    @Test
    void minioBindsIndependentPropertiesAndIsSelectedInProduction() {
        runner.withPropertyValues("file.storage.type=minio",
                "file.storage.minio.endpoint=http://localhost:9000",
                "file.storage.minio.bucket=flow-files",
                "file.storage.minio.access-key=minio-test-access",
                "file.storage.minio.secret-key=minio-test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(FileStorageProvider.class);
                    FileStorageProvider strategy = context.getBean(FileStorageFactory.class).getStrategy();
                    assertThat(strategy).isExactlyInstanceOf(MinioFileStorageStrategy.class);
                    assertThat(strategy.getStorageType()).isEqualTo("minio");
                    assertThat(strategy.getAccessUrl("image.png")).isEqualTo("s3://flow-files/image.png");
                });
    }

    @Test
    void existingS3ConfigurationDoesNotRequireMinioProperties() {
        runner.withPropertyValues("file.storage.type=s3", "file.storage.s3.bucket=existing-files")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(FileStorageProvider.class)
                            .doesNotHaveBean(MinioFileStorageStrategy.class);
                    assertThat(context.getBean(FileStorageFactory.class).getStrategy())
                            .isExactlyInstanceOf(S3FileStorageStrategy.class);
                });
    }

    // 扫描实际配置包，使测试覆盖生产启动时的组件发现，避免重新引入存储类型限制后仍通过。
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = FileStorageProperties.class)
    static class StoragePropertiesConfiguration {
    }
}
