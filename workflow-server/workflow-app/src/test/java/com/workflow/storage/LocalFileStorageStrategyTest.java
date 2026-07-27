package com.workflow.storage;

import com.workflow.storage.infrastructure.config.FileStorageProperties;
import com.workflow.storage.infrastructure.local.LocalFileStorageStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageStrategyTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsPublicAccessUrlInsteadOfPhysicalStoragePath() throws Exception {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getLocal().setPath(tempDir.toString());
        properties.getLocal().setAccessUrl("/uploads/");
        LocalFileStorageStrategy strategy = new LocalFileStorageStrategy(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "acceptance.png",
                "image/png",
                new byte[]{1, 2, 3});

        Map<String, String> result = strategy.upload(file);

        assertThat(result.get("url")).startsWith("/uploads/");
        assertThat(result.get("url")).doesNotContain(tempDir.toString());
        assertThat(Files.exists(tempDir.resolve(result.get("filename")))).isTrue();
    }

    @Test
    void writesRelativeStoragePathWithoutServletContainerPathResolution() throws Exception {
        Path relativeDirectory = Path.of(
                "target",
                "relative-upload-" + UUID.randomUUID());
        Path absoluteDirectory = relativeDirectory.toAbsolutePath().normalize();
        FileStorageProperties properties = new FileStorageProperties();
        properties.getLocal().setPath(relativeDirectory.toString());
        LocalFileStorageStrategy strategy = new LocalFileStorageStrategy(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "acceptance.txt",
                "text/plain",
                "relative upload".getBytes()) {
            @Override
            public void transferTo(File dest) throws IOException {
                throw new IOException("Servlet container relative path resolution must not be used");
            }
        };

        try {
            Map<String, String> result = strategy.upload(file);

            assertThat(Files.readString(absoluteDirectory.resolve(result.get("filename"))))
                    .isEqualTo("relative upload");
        } finally {
            if (Files.exists(absoluteDirectory)) {
                try (var files = Files.list(absoluteDirectory)) {
                    files.forEach(path -> path.toFile().delete());
                }
                Files.deleteIfExists(absoluteDirectory);
            }
        }
    }
}
