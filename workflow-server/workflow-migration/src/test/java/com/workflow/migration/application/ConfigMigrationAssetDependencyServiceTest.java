package com.workflow.migration.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetDependencyMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAssetDependency;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigMigrationAssetDependencyServiceTest {

    @Test
    void persistsStableSourceVersionLocationStrengthAndParseStatus() {
        ConfigMigrationAssetDependencyMapper mapper = mock(ConfigMigrationAssetDependencyMapper.class);
        JsonDocumentCodec codec = mock(JsonDocumentCodec.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("asset-1"))).thenReturn(List.of(Map.of(
                "assetType", "FORM",
                "businessKey", "leave-form",
                "sourceVersion", 7)));
        when(codec.write(any(), anyString())).thenReturn("{}");
        ConfigMigrationAssetDependencyService service =
                new ConfigMigrationAssetDependencyService(mapper, codec, jdbcTemplate);

        service.replace("asset-1", List.of(Map.of(
                "type", "ENTITY_FIELD",
                "key", "leave_request.applicant_id",
                "required", true,
                "location", "$.fields[stableKey=applicant_id]",
                "strength", "hard",
                "parseStatus", "resolved")));

        ArgumentCaptor<ConfigMigrationAssetDependency> captor =
                ArgumentCaptor.forClass(ConfigMigrationAssetDependency.class);
        verify(mapper).deleteByAssetId("asset-1");
        verify(mapper).insert(captor.capture());
        ConfigMigrationAssetDependency dependency = captor.getValue();
        assertEquals("FORM", dependency.getSourceAssetType());
        assertEquals("leave-form", dependency.getSourceBusinessKey());
        assertEquals(7, dependency.getSourceVersion());
        assertEquals("$.fields[stableKey=applicant_id]", dependency.getReferenceLocation());
        assertEquals("HARD", dependency.getDependencyStrength());
        assertEquals("RESOLVED", dependency.getParseStatus());
        assertNotNull(dependency.getExtractedAt());
    }
}
