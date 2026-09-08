package com.workflow.entity.mutationpolicy.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.mutationpolicy.application.model.EntityMutationPolicyDocument;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.mapper.EntityMutationPolicyConfigMapper;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.mapper.EntityMutationPolicyReleaseMapper;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.record.EntityMutationPolicyConfig;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.record.EntityMutationPolicyRelease;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityMutationPolicyServiceTest {

    @Mock
    private EntityMutationPolicyConfigMapper configMapper;
    @Mock
    private EntityMutationPolicyReleaseMapper releaseMapper;
    @Mock
    private EntityDefinitionMapper definitionMapper;
    @Mock
    private EntityMutationPolicyValidator validator;

    private ObjectMapper objectMapper;
    private EntityMutationPolicyService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new EntityMutationPolicyService(
                configMapper,
                releaseMapper,
                definitionMapper,
                validator,
                objectMapper);
    }

    @Test
    void newEntityStartsWithAnEmptyNativeDraft() {
        when(definitionMapper.findByEntityCode("asset"))
                .thenReturn(Optional.of(definition("asset")));
        EntityMutationPolicyDocument draft = service.getDraft("asset");
        assertEquals("NATIVE", draft.getMigrationState());
        assertEquals("UNCONFIGURED", draft.getStatus());
        assertTrue(draft.getSteps().isEmpty());
        assertTrue(draft.getTargetBindings().isEmpty());
        verify(configMapper, never()).insert(org.mockito.ArgumentMatchers.any(EntityMutationPolicyConfig.class));
    }

    @Test
    void unpublishedDraftDoesNotBecomeRuntimePolicy() {
        EntityMutationPolicyConfig config = new EntityMutationPolicyConfig();
        config.setDraftDocument("{\"enabled\":true,\"steps\":[{}]}");
        when(configMapper.findByEntityCode("asset")).thenReturn(config);
        assertTrue(service.getPublished("asset").isEmpty());
    }

    @Test
    void readsOnlyTheActivatedMutationRelease()
            throws Exception {
        EntityMutationPolicyConfig config = new EntityMutationPolicyConfig();
        config.setId("config-1");
        config.setEntityCode("asset");
        config.setActiveReleaseId("release-1");
        EntityMutationPolicyDocument document =
                new EntityMutationPolicyDocument();
        document.setEnabled(true);
        EntityMutationPolicyRelease release =
                new EntityMutationPolicyRelease();
        release.setId("release-1");
        release.setVersion(2);
        release.setConfigDocument(
                objectMapper.writeValueAsString(document));
        when(configMapper.findByEntityCode("asset"))
                .thenReturn(config);
        when(releaseMapper.selectById("release-1"))
                .thenReturn(release);

        var published = service.getPublished("asset");

        assertTrue(published.isPresent());
        assertEquals(2, published.orElseThrow()
                .getActiveReleaseVersion());
        assertEquals("MIGRATED", published.orElseThrow()
                .getMigrationState());
    }

    @Test
    void staleDraftRevisionIsRejected() {
        EntityDefinition definition = definition("asset");
        EntityMutationPolicyConfig current =
                new EntityMutationPolicyConfig();
        current.setRevision(4);
        when(definitionMapper.findByEntityCode("asset"))
                .thenReturn(Optional.of(definition));
        when(configMapper.findByEntityCodeForUpdate("asset"))
                .thenReturn(current);

        assertThrows(BusinessConflictException.class,
                () -> service.saveDraft(
                        "asset",
                        new EntityMutationPolicyDocument(),
                        3));
    }

    @Test
    void existingDraftRequiresRevision() {
        EntityDefinition definition = definition("asset");
        EntityMutationPolicyConfig current =
                new EntityMutationPolicyConfig();
        current.setRevision(4);
        when(definitionMapper.findByEntityCode("asset"))
                .thenReturn(Optional.of(definition));
        when(configMapper.findByEntityCodeForUpdate("asset"))
                .thenReturn(current);

        assertThrows(BusinessConflictException.class,
                () -> service.saveDraft(
                        "asset",
                        new EntityMutationPolicyDocument(),
                        null));
    }

    private EntityDefinition definition(String code) {
        EntityDefinition value = new EntityDefinition();
        value.setId("entity-1");
        value.setEntityCode(code);
        value.setEntityName("资产");
        return value;
    }

}
