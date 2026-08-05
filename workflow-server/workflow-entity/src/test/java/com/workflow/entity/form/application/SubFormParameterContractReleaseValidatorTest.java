package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubFormParameterContractReleaseValidatorTest {

    @Test
    void validatesContractAgainstPinnedChildReleaseSnapshot() {
        Fixture fixture = fixture(3);

        assertDoesNotThrow(() ->
                fixture.validator().validateSnapshot(
                        fixture.parentForm()));
    }

    @Test
    void rejectsPinnedChildReleaseVersionMismatch() {
        Fixture fixture = fixture(4);

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.validator().validateSnapshot(
                        fixture.parentForm()));
    }

    private Fixture fixture(int actualReleaseVersion) {
        EntityFormMapper formMapper =
                mock(EntityFormMapper.class);
        EntityFieldMapper fieldMapper =
                mock(EntityFieldMapper.class);
        EntityRelationMapper relationMapper =
                mock(EntityRelationMapper.class);
        UiConfigReleaseMapper releaseMapper =
                mock(UiConfigReleaseMapper.class);
        JsonDocumentCodec codec =
                new JsonDocumentCodec(new ObjectMapper());

        EntityFormNode node = new EntityFormNode();
        node.setId("node-lines");
        node.setNodeKey("lines");
        node.setNodeType("SUB_FORM");
        node.setBindingType("NONE");
        node.setPropsDocument("""
                {
                  "componentProps": {
                    "subFormConfig": {
                      "childFormId": "child-form",
                      "childFormReleaseId": "release-3",
                      "childFormReleaseVersion": 3,
                      "childRefFieldCode": "parent_id",
                      "parameterContract": {
                        "version": 1,
                        "parameterMapping": {
                          "projectId": {
                            "literal": "PROJECT-1"
                          }
                        },
                        "fieldInitializationMapping": {
                          "source_dept_id": {
                            "literal": "DEPT-1"
                          }
                        }
                      }
                    }
                  }
                }
                """);

        EntityForm parentForm = new EntityForm();
        parentForm.setId("parent-form");
        parentForm.setEntityId("parent-entity");
        parentForm.setNodes(List.of(node));

        UiConfigRelease release = new UiConfigRelease();
        release.setId("release-3");
        release.setConfigType("FORM");
        release.setConfigId("child-form");
        release.setVersion(actualReleaseVersion);
        release.setSnapshotDocument("""
                {
                  "form": {
                    "viewConfig": {
                      "inputParameterSchema": {
                        "type": "object",
                        "required": ["projectId"],
                        "properties": {
                          "projectId": {
                            "type": "string",
                            "title": "项目ID"
                          }
                        }
                      }
                    }
                  },
                  "legacyFields": [
                    {
                      "fieldCode": "source_dept_id",
                      "fieldName": "来源部门",
                      "fieldType": "STRING",
                      "isReadonly": false
                    }
                  ]
                }
                """);
        when(releaseMapper.selectById("release-3"))
                .thenReturn(release);
        when(fieldMapper.findByEntityId("parent-entity"))
                .thenReturn(List.of());

        return new Fixture(
                new SubFormParameterContractReleaseValidator(
                        formMapper,
                        fieldMapper,
                        relationMapper,
                        releaseMapper,
                        codec),
                parentForm);
    }

    private record Fixture(
            SubFormParameterContractReleaseValidator validator,
            EntityForm parentForm) {
    }
}
