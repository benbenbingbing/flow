package com.workflow.service;

import com.workflow.entity.ui.application.UiComponentTemplateService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.api.request.UiComponentTemplateUpgradeRequest;
import com.workflow.entity.ui.api.request.UiComponentTemplateSaveRequest;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplate;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplateVersion;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiComponentTemplateVersionMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UI 组件模板服务测试。
 *
 * <p>被测对象：{@link UiComponentTemplateService}，覆盖模板显式升级时保留本地改动并追加新模板字段的合并场景。
 */
class UiComponentTemplateServiceTest {

    /**
     * 列模板只能携带可复用配置，不得把具体字段编码或排序保存进模板。
     */
    @Test
    void listColumnTemplateRejectsConcreteFieldIdentity() {
        ObjectMapper objectMapper = new ObjectMapper();
        UiComponentTemplateSaveRequest request =
                new UiComponentTemplateSaveRequest();
        request.setTemplateKey("COMMON_STATUS_COLUMN");
        request.setTemplateName("通用状态列");
        request.setTemplateType("LIST_COLUMN_GROUP");
        request.setSnapshot(Map.of(
                "field",
                Map.of(
                        "fieldCode", "status",
                        "width", 140)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new UiComponentTemplateService(
                        mock(UiComponentTemplateMapper.class),
                        mock(UiComponentTemplateVersionMapper.class),
                        new JsonDocumentCodec(objectMapper))
                        .save(request));

        assertTrue(error.getMessage().contains("fieldCode"));
    }

    /**
     * 测试显式升级保留本地改动并追加新模板字段：
     * 验证合并快照中本地标题被保留、新字段 color 被追加，且标记需要用户确认。
     */
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
        template.setTemplateType("BUTTON_GROUP");
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

    /**
     * 列表列模板只用于初始化，不允许通过模板升级修改已经配置的列。
     */
    @Test
    void listColumnTemplateRejectsUpgrade() {
        UiComponentTemplateMapper templateMapper =
                mock(UiComponentTemplateMapper.class);
        UiComponentTemplate template = new UiComponentTemplate();
        template.setId("tpl-1");
        template.setCurrentVersion(2);
        template.setDeleted(0);
        template.setTemplateType("LIST_COLUMN_GROUP");
        when(templateMapper.selectById("tpl-1")).thenReturn(template);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new UiComponentTemplateService(
                        templateMapper,
                        mock(UiComponentTemplateVersionMapper.class),
                        new JsonDocumentCodec(new ObjectMapper()))
                        .upgrade("tpl-1", new UiComponentTemplateUpgradeRequest()));

        assertTrue(error.getMessage().contains("一次性初始化"));
    }

    /**
     * 列表列模板拒绝版本历史查询，并通过当前快照接口读取配置。
     */
    @Test
    void listColumnTemplateUsesCurrentSnapshotWithoutVersionHistory() {
        JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
        UiComponentTemplateMapper templateMapper =
                mock(UiComponentTemplateMapper.class);
        UiComponentTemplateVersionMapper versionMapper =
                mock(UiComponentTemplateVersionMapper.class);
        UiComponentTemplate template = new UiComponentTemplate();
        template.setId("tpl-1");
        template.setCurrentVersion(2);
        template.setDeleted(0);
        template.setTemplateType("LIST_COLUMN_GROUP");
        when(templateMapper.selectById("tpl-1")).thenReturn(template);
        when(versionMapper.selectOne(any())).thenReturn(
                version(codec, 2, Map.of("field", Map.of("width", 140))));

        UiComponentTemplateService service = new UiComponentTemplateService(
                templateMapper,
                versionMapper,
                codec);
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.versions("tpl-1"));
        assertTrue(error.getMessage().contains("不提供版本历史"));

        Map<String, Object> snapshot = service.currentSnapshot("tpl-1");
        assertEquals(140, ((Map<?, ?>) snapshot.get("field")).get("width"));
    }

    /** 构造带完整性哈希的模板版本对象 */
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

    /** 计算字符串的 SHA-256 十六进制哈希，用于模拟模板快照完整性哈希 */
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
