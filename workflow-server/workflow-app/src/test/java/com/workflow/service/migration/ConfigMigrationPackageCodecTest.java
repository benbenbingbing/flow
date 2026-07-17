package com.workflow.service.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.migration.ConfigMigrationAsset;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationPackageCodecTest {

    @Test
    void signedPackageRoundTripsSelectedSnapshot() {
        ConfigMigrationPackageCodec codec = codec("test-signing-key");
        ConfigMigrationAsset asset = entityAsset();

        ConfigMigrationPackageCodec.EncodedPackage encoded = codec.encode(
                "WFP-TEST-001",
                "REL-20260716-001",
                List.of(asset),
                Map.of(asset.getId(), Map.of(
                        "full", false,
                        "sections", List.of("forms"))));

        ConfigMigrationPackageCodec.DecodedPackage decoded = codec.decode(encoded.data());

        assertEquals("WFP-TEST-001", decoded.packageNo());
        assertEquals("REL-20260716-001", decoded.migrationTag());
        assertEquals(1, decoded.assets().size());
        Map<String, Object> snapshot = decoded.assets().get(0).snapshot();
        assertTrue(snapshot.containsKey("definition"));
        assertTrue(snapshot.containsKey("forms"));
        assertFalse(snapshot.containsKey("fields"));
        assertEquals(encoded.checksum(), decoded.checksum());
    }

    @Test
    void packageSignedByDifferentEnvironmentIsRejected() {
        ConfigMigrationPackageCodec source = codec("source-key");
        ConfigMigrationPackageCodec target = codec("target-key");
        byte[] data = source.encode(
                "WFP-TEST-002",
                "REL-20260716-002",
                List.of(entityAsset()),
                Map.of()).data();

        assertThrows(IllegalArgumentException.class, () -> target.decode(data));
    }

    private ConfigMigrationPackageCodec codec(String signingKey) {
        ConfigMigrationPackageCodec codec = new ConfigMigrationPackageCodec(
                new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(codec, "signingKey", signingKey);
        ReflectionTestUtils.setField(codec, "environmentName", "TEST");
        return codec;
    }

    private ConfigMigrationAsset entityAsset() {
        ConfigMigrationAsset asset = new ConfigMigrationAsset();
        asset.setId("asset-1");
        asset.setAssetType(ConfigMigrationAssetService.ENTITY);
        asset.setBusinessKey("expense");
        asset.setAssetName("费用申请");
        asset.setSourceVersion(3);
        asset.setContentHash("full-hash");
        asset.setSnapshotSchemaVersion(1);
        asset.setSnapshotJson("""
                {
                  "schemaVersion": 1,
                  "assetType": "ENTITY",
                  "businessKey": "expense",
                  "definition": {"entityCode": "expense", "entityName": "费用申请"},
                  "fields": [{"fieldCode": "amount", "fieldType": "DECIMAL"}],
                  "forms": [{"formKey": "default", "formName": "默认表单"}],
                  "dependencies": []
                }
                """);
        asset.setDependenciesJson("[]");
        return asset;
    }
}
