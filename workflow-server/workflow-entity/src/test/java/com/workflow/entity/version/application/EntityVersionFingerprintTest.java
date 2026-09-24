package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 固定摘要防止抽取工具时改变已入库指纹，导致旧冻结范围无法再采集。 */
class EntityVersionFingerprintTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void schemaHashRetainsExistingCanonicalBytesAndNullCollectionCompatibility() throws Exception {
        var snapshot = snapshot("h1", "expense"); snapshot.setEntityId("e1"); snapshot.setVersion(2);
        snapshot.setRelationsSnapshotAvailable(true);
        assertEquals("861c58c1d202fee54df4144f601173c969514c0dabca1bd4f8362a71fe3035a8", EntityVersionFingerprint.hash(mapper, EntityVersionFingerprint.entitySchemaMaterial(snapshot)));
        snapshot.setFields(List.of()); snapshot.setRelations(List.of());
        assertEquals("861c58c1d202fee54df4144f601173c969514c0dabca1bd4f8362a71fe3035a8", EntityVersionFingerprint.hash(mapper, EntityVersionFingerprint.entitySchemaMaterial(snapshot)));
    }

    @Test
    void relationHashRetainsFallbackTrimmingAndNullableFlags() throws Exception {
        var relation = new EntityRelation(); relation.setRelationCode("items");
        relation.setDataKey(" "); relation.setParentFieldCode(" children "); relation.setChildRefFieldCode("parent_id");
        relation.setCascadeDelete(null); relation.setRequired(null); relation.setEnabled(null);
        assertEquals("0c474debb0be6dd59223c3cf69d6871b3b5817670ee9d06f7e155c86240f419e", EntityVersionFingerprint.hash(mapper,
                EntityVersionFingerprint.relationDefinitionMaterial(snapshot("h1", "expense"), snapshot("h2", "line"), relation)));
    }

    private EntityPublishedSnapshot snapshot(String historyId, String code) {
        var snapshot = new EntityPublishedSnapshot(); snapshot.setHistoryId(historyId); snapshot.setEntityCode(code); return snapshot;
    }
}
