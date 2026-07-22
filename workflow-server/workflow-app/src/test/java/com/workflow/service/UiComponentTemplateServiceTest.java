package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.UiComponentTemplateUpgradeRequest;
import com.workflow.entity.UiComponentTemplate;
import com.workflow.entity.UiComponentTemplateVersion;
import com.workflow.mapper.UiComponentTemplateMapper;
import com.workflow.mapper.UiComponentTemplateVersionMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiComponentTemplateServiceTest {

    @Test
    void explicitUpgradeKeepsLocalChangesAndAddsNewTemplateFields() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonDocumentCodec codec = new JsonDocumentCodec(objectMapper);
        UiComponentTemplateMapper templateMapper =
                mock(UiComponentTemplateMapper.class);
        UiComponentTemplateVersionMapper versionMapper =
                mock(UiComponentTemplateVersionMapper.class);

        UiComponentTemplate template = new UiComponentTemplate();
        template.setId("tpl-1");
        template.setCurrentVersion(2);
        template.setDeleted(0);
        when(templateMapper.selectById("tpl-1")).thenReturn(template);

        UiComponentTemplateVersion base = version(
                codec,
                1,
                Map.of("title", "基础标题", "width", 12));
        UiComponentTemplateVersion incoming = version(
                codec,
                2,
                Map.of("title", "模板新标题", "width", 12, "color", "blue"));
        when(versionMapper.selectOne(any())).thenReturn(base, incoming);

        UiComponentTemplateUpgradeRequest request =
                new UiComponentTemplateUpgradeRequest();
        request.setFromVersion(1);
        request.setToVersion(2);
        request.setCurrentSnapshot(
                Map.of("title", "本地标题", "width", 12));

        Map<String, Object> result = new UiComponentTemplateService(
                templateMapper,
                versionMapper,
                codec).upgrade("tpl-1", request);
        Map<?, ?> merged = (Map<?, ?>) result.get("mergedSnapshot");

        assertEquals("本地标题", merged.get("title"));
        assertEquals("blue", merged.get("color"));
        assertTrue((Boolean) result.get("requiresConfirmation"));
    }

    private UiComponentTemplateVersion version(
            JsonDocumentCodec codec,
            int version,
            Map<String, Object> snapshot) {
        UiComponentTemplateVersion value = new UiComponentTemplateVersion();
        value.setTemplateId("tpl-1");
        value.setVersion(version);
        String document = codec.write(snapshot, "模板快照");
        value.setSnapshotDocument(document);
        value.setContentHash(sha256(document));
        return value;
    }

    private String sha256(String document) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(document.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
