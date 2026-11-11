package com.workflow.migration.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.workflow.admin.setting.application.GlobalSettingService;
import com.workflow.migration.infrastructure.archive.WfpackArchive;
import com.workflow.migration.infrastructure.crypto.WfpackIntegrity;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigAssetBaselineMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportItemMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportPackageMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.workflow.admin.setting.application.GlobalSettingRegistry.MIGRATION_SIGNING_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用真实 ZIP、校验和及上传服务重放语义反例，并验证失败发生在任何目标查询/批次写入前。
 * 因为未触及目标状态，拒绝结果不依赖目标是空库、内容一致还是等待升级。
 */
@ExtendWith(MockitoExtension.class)
class ConfigMigrationPackageSemanticValidationTest {
    private static final String KEY = "semantic-validation-test-key";
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    @Mock ConfigMigrationAssetService assetService;
    @Mock ConfigImportPackageMapper importPackageMapper;
    @Mock ConfigImportItemMapper importItemMapper;
    @Mock ConfigAssetBaselineMapper baselineMapper;
    @Spy ConfigMigrationPackageCodec packageCodec = codec();
    @InjectMocks ConfigMigrationPackageService service;

    /** 原始实机失败包原样重放：未确认、已确认来源都必须在写库前拒绝。 */
    @ParameterizedTest(name = "原始反例 {0}: {1}")
    @CsvSource({"S08,sourceVersion", "S09,sourceVersion", "S13,快照版本", "S15,packageNo",
            "S16,packageNo", "S18,selection.sections", "S23,dependencies.json"})
    void originalFailingPackagesAreRejectedBeforeAnyImportWrites(String id, String expectedMessage) throws Exception {
        byte[] bytes = resource(id);
        assertRejectedBeforeWrites(bytes, expectedMessage);
    }

    @ParameterizedTest(name = "非法清单: {0}")
    @MethodSource("invalidManifests")
    void malformedManifestIsRejectedBeforeAnyImportWrites(String name, String message,
                                                          Consumer<Map<String, Object>> edit) throws Exception {
        assertRejectedBeforeWrites(rewrite(edit, snapshot -> {}, null), message);
    }

    private static Stream<Arguments> invalidManifests() {
        return Stream.of(
                invalid("零版本", "sourceVersion", m -> asset(m).put("sourceVersion", 0)),
                invalid("负版本", "sourceVersion", m -> asset(m).put("sourceVersion", -1)),
                invalid("缺失版本", "sourceVersion", m -> asset(m).remove("sourceVersion")),
                invalid("null版本", "sourceVersion", m -> asset(m).put("sourceVersion", null)),
                invalid("字符串版本", "sourceVersion", m -> asset(m).put("sourceVersion", "1")),
                invalid("小数版本", "sourceVersion", m -> asset(m).put("sourceVersion", 1.5)),
                invalid("浮点整数版本", "sourceVersion", m -> asset(m).put("sourceVersion", 1.0)),
                invalid("布尔版本", "sourceVersion", m -> asset(m).put("sourceVersion", true)),
                invalid("超出int版本", "sourceVersion", m -> asset(m).put("sourceVersion", 2147483648L)),
                invalid("超出long版本", "sourceVersion", m -> asset(m).put("sourceVersion", new BigInteger("99999999999999999999999"))),
                invalid("缺失快照版本", "snapshotSchemaVersion", m -> asset(m).remove("snapshotSchemaVersion")),
                invalid("null快照版本", "snapshotSchemaVersion", m -> asset(m).put("snapshotSchemaVersion", null)),
                invalid("零快照版本", "snapshotSchemaVersion", m -> asset(m).put("snapshotSchemaVersion", 0)),
                invalid("未来快照版本", "快照版本", m -> asset(m).put("snapshotSchemaVersion", 999)),
                invalid("不同受支持快照版本", "快照版本不一致", m -> asset(m).put("snapshotSchemaVersion", 2)),
                invalid("缺失包编号", "packageNo", m -> m.remove("packageNo")),
                invalid("null包编号", "packageNo", m -> m.put("packageNo", null)),
                invalid("空包编号", "packageNo", m -> m.put("packageNo", "")),
                invalid("空白包编号", "packageNo", m -> m.put("packageNo", " \t")),
                invalid("首尾空格包编号", "packageNo", m -> m.put("packageNo", " WFP-1 ")),
                invalid("错误类型包编号", "packageNo", m -> m.put("packageNo", 123)),
                invalid("超长包编号", "packageNo", m -> m.put("packageNo", "x".repeat(101))),
                invalid("控制字符包编号", "packageNo", m -> m.put("packageNo", "WFP\n1")),
                invalid("历史假null包编号", "packageNo", m -> m.put("packageNo", "null")),
                invalidSelection("未知分区", Map.of("full", false, "sections", List.of("UNKNOWN"))),
                invalidSelection("混合未知分区", Map.of("full", false, "sections", List.of("forms", "UNKNOWN"))),
                invalidSelection("全量仍夹带未知分区", Map.of("full", true, "sections", List.of("UNKNOWN"))),
                invalidSelection("实体不能选择流程分区", Map.of("full", false, "sections", List.of("bpmnXml"))),
                invalidSelection("全量夹带跨类型分区", Map.of("full", true, "sections", List.of("bpmnXml"))),
                invalidSelection("分区不是数组", Map.of("full", false, "sections", "forms")),
                invalidSelection("分区不是字符串", Map.of("full", false, "sections", List.of(1))),
                invalidSelection("分区为空字符串", Map.of("full", false, "sections", List.of(""))));
    }

    @ParameterizedTest(name = "非法快照正文版本: {0}")
    @ValueSource(strings = {"null", "missing", "0", "999", "1.5"})
    void invalidBodySchemaCannotHideBehindValidManifest(String value) throws Exception {
        byte[] bytes = rewrite(manifest -> {}, snapshot -> {
            if ("missing".equals(value)) snapshot.remove("schemaVersion");
            else {
                try { snapshot.put("schemaVersion", json.readValue(value, Object.class)); }
                catch (IOException e) { throw new IllegalStateException(e); }
            }
        }, null);
        assertRejectedBeforeWrites(bytes, "schemaVersion");
    }

    @ParameterizedTest(name = "非法依赖清单: {0}")
    @ValueSource(strings = {"{broken", "{}", "null", "1", "[] {}", "[null]", "[1]", "[{}]",
            "[{\"type\":\"USER\"}]", "[{\"type\":\"USER\",\"key\":\"\"}]",
            "[{\"type\":1,\"key\":\"alice\"}]", "[{\"type\":\"USER\",\"key\":\"alice\",\"required\":\"true\"}]",
            "[{\"type\":\"USER\",\"key\":\"alice\",\"targetOnly\":null}]"})
    void malformedDependencyFileIsRejectedEvenWithValidSignatureAndChecksums(String dependencies) throws Exception {
        assertRejectedBeforeWrites(rewrite(manifest -> {}, snapshot -> {}, dependencies), "dependencies.json");
    }

    @Test
    void missingDependencyFileIsRejected() throws Exception {
        Map<String, byte[]> entries = WfpackArchive.unzip(validPackage());
        entries.remove("dependencies.json");
        assertRejectedBeforeWrites(repack(entries), "dependencies.json");
    }

    /** 格式和快照版本是独立坐标，历史快照 1/2 与当前快照 3 均继续支持。 */
    @ParameterizedTest(name = "兼容包格式 {0}，快照版本 {1}")
    @CsvSource({"3,1", "3,2", "3,3", "4,1", "4,2", "4,3"})
    void supportedPackageAndSnapshotVersionsRoundTrip(int format, int schema) throws Exception {
        byte[] bytes = rewrite(manifest -> {
            manifest.put("formatVersion", format);
            asset(manifest).put("snapshotSchemaVersion", schema);
        }, snapshot -> snapshot.put("schemaVersion", schema), null);
        var decoded = packageCodec.decode(bytes);
        assertTrue(decoded.signatureVerified());
        assertEquals(schema, decoded.assets().get(0).snapshot().get("schemaVersion"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"definition", "fields", "relations", "statuses", "codeRule", "forms", "lists",
            "dataPermissions", "scopePolicies", "scopeBindings", "menus", "interfaceExtensions", "dataSources",
            "extensions", "eventBindings", "excelConfiguration"})
    void supportedEntitySectionsAndLegacyAliasRemainReadable(String section) throws Exception {
        var decoded = packageCodec.decode(rewrite(manifest -> asset(manifest).put("selection",
                Map.of("full", false, "sections", List.of(section))), snapshot -> {}, null));
        assertEquals(List.of(section), ((Map<?, ?>) decoded.assets().get(0).snapshot().get("_selection")).get("sections"));
    }

    @ParameterizedTest
    @CsvSource({"PROCESS,definition|bpmnXml|nodes|nodeForms|nodeApprovals|flowActions|statusMappings",
            "SYSTEM_ENTITY_UI,forms|lists|interfaceExtensions|dataSources|extensions|eventBindings",
            "WORK_CALENDAR,configuration", "TASK_SLA_POLICY,configuration", "DICTIONARY,definition|items"})
    void otherAssetTypesKeepTheirSupportedSections(String type, String names) {
        Map<String, Object> snapshot = Map.of("assetType", type, "definition", Map.of(), "dependencies", List.of());
        for (String section : names.split("\\|")) {
            var selected = packageCodec.selectSnapshot(snapshot,
                    Map.of("full", false, "sections", List.of(section)));
            assertEquals(List.of(section), ((Map<?, ?>) selected.get("_selection")).get("sections"));
        }
    }

    @Test
    void originalValidFullPackageStillDecodes() throws Exception {
        assertEquals(2, packageCodec.decode(resource("MX_BASELINE")).assets().size());
    }

    @Test
    void validMaximumVersionAndHistoricalPackageLabelArePreserved() throws Exception {
        String label = "历史包_" + "x".repeat(96);
        var decoded = packageCodec.decode(rewrite(manifest -> {
            manifest.put("packageNo", label);
            asset(manifest).put("sourceVersion", Integer.MAX_VALUE);
        }, snapshot -> {}, "[{\"type\":\"USER\",\"key\":\"alice\",\"customMetadata\":1}]"));
        assertEquals(label, decoded.packageNo());
        assertEquals(Integer.MAX_VALUE, decoded.assets().get(0).sourceVersion());
    }

    /** 两种确认状态都重放，防止将管理员确认来源错误地当成语义校验豁免。 */
    private void assertRejectedBeforeWrites(byte[] bytes, String message) {
        for (String confirmation : new String[]{null, WfpackIntegrity.sha256(bytes)}) {
            var file = new MockMultipartFile("file", "test.wfpack", "application/octet-stream", bytes);
            var error = assertThrows(IllegalArgumentException.class,
                    () -> service.importPackage(file, "TEST", confirmation));
            assertTrue(error.getMessage().contains(message), error.getMessage());
        }
        verifyNoInteractions(importPackageMapper, importItemMapper, baselineMapper, assetService);
    }

    /** 改正文时同步原始资产哈希与所有文件校验和，让失败准确来自语义检查。 */
    private byte[] rewrite(Consumer<Map<String, Object>> manifestEdit,
                           Consumer<Map<String, Object>> snapshotEdit, String dependencyJson) throws Exception {
        Map<String, byte[]> entries = WfpackArchive.unzip(validPackage());
        Map<String, Object> manifest = json.readValue(entries.get("manifest.json"), new TypeReference<>() {});
        Map<String, Object> asset = asset(manifest);
        String path = asset.get("path").toString();
        Map<String, Object> snapshot = json.readValue(entries.get(path), new TypeReference<>() {});
        snapshotEdit.accept(snapshot);
        entries.put(path, json.writeValueAsBytes(snapshot));
        asset.put("sourceHash", packageCodec.hashSnapshot(snapshot));
        manifestEdit.accept(manifest);
        entries.put("manifest.json", json.writeValueAsBytes(manifest));
        if (dependencyJson != null) entries.put("dependencies.json", dependencyJson.getBytes(StandardCharsets.UTF_8));
        return repack(entries);
    }

    private byte[] repack(Map<String, byte[]> entries) throws Exception {
        Map<String, String> checksums = new LinkedHashMap<>();
        entries.forEach((path, bytes) -> {
            if (!List.of("checksums.json", "signature.sig").contains(path)) checksums.put(path, WfpackIntegrity.sha256(bytes));
        });
        byte[] bytes = json.writeValueAsBytes(checksums);
        entries.put("checksums.json", bytes);
        entries.put("signature.sig", WfpackIntegrity.hmac(bytes, KEY).getBytes(StandardCharsets.UTF_8));
        return WfpackArchive.zip(entries);
    }

    private byte[] validPackage() throws Exception {
        var asset = new ConfigMigrationAsset();
        asset.setId("entity"); asset.setAssetType("ENTITY"); asset.setBusinessKey("test_entity");
        asset.setAssetName("测试实体"); asset.setSourceVersion(3); asset.setSnapshotSchemaVersion(3);
        asset.setSnapshotJson(json.writeValueAsString(Map.of("schemaVersion", 3, "assetType", "ENTITY",
                "businessKey", "test_entity", "definition", Map.of("entityCode", "test_entity"),
                "forms", List.of(Map.of("formKey", "main")), "dependencies", List.of())));
        return packageCodec.encode("WFP-SEMANTIC", "TEST", List.of(asset), Map.of()).data();
    }

    private byte[] resource(String name) throws IOException {
        try (var stream = getClass().getResourceAsStream("/wfpack-semantic/" + name + ".wfpack")) {
            assertNotNull(stream, name);
            return stream.readAllBytes();
        }
    }

    private ConfigMigrationPackageCodec codec() {
        var settings = mock(GlobalSettingService.class);
        lenient().when(settings.readSystemValue(MIGRATION_SIGNING_KEY)).thenReturn(TextNode.valueOf(KEY));
        var codec = new ConfigMigrationPackageCodec(json, settings);
        ReflectionTestUtils.setField(codec, "environmentName", "TEST");
        return codec;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asset(Map<String, Object> manifest) {
        return ((List<Map<String, Object>>) manifest.get("assets")).get(0);
    }

    private static Arguments invalid(String name, String message, Consumer<Map<String, Object>> edit) {
        return Arguments.of(name, message, edit);
    }

    private static Arguments invalidSelection(String name, Map<String, Object> selection) {
        return invalid(name, "selection.sections", manifest -> asset(manifest).put("selection", selection));
    }
}
