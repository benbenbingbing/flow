package com.workflow.entity.definition.application;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityCodeRuleMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityCodeRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityCodeGeneratorServiceTest {

    @Mock
    private EntityCodeRuleMapper codeRuleMapper;

    @Mock
    private EntityDefinitionAccessPolicy entityAccessPolicy;

    private EntityCodeGeneratorService service;

    @BeforeEach
    void setUp() {
        service = new EntityCodeGeneratorService(
                codeRuleMapper,
                entityAccessPolicy);
    }

    @Test
    void saveNewRuleDiscardsClientPrimaryKey() {
        EntityCodeRule rule = rule("  new_entity  ");
        rule.setId("stale-rule-id");
        rule.setCurrentSeq(99);
        rule.setSeqDate("20260731");
        rule.setCreatedAt(LocalDateTime.of(
                2026, 7, 31, 10, 0));
        when(codeRuleMapper.findByEntityCode(
                "new_entity")).thenReturn(Optional.empty());

        service.saveRule(rule);

        verify(entityAccessPolicy)
                .requireDynamicByCodeForUpdate("new_entity");
        ArgumentCaptor<EntityCodeRule> captor =
                ArgumentCaptor.forClass(EntityCodeRule.class);
        verify(codeRuleMapper).insert(captor.capture());
        EntityCodeRule inserted = captor.getValue();
        assertNull(inserted.getId());
        assertEquals("new_entity", inserted.getEntityCode());
        assertEquals(0, inserted.getCurrentSeq());
        assertEquals("", inserted.getSeqDate());
        assertNull(inserted.getCreatedAt());
        assertNull(inserted.getUpdatedAt());
        verify(codeRuleMapper, never()).updateById(rule);
    }

    @Test
    void saveExistingRuleUsesPersistedIdentityAndSequence() {
        EntityCodeRule current = rule("asset");
        current.setId("persisted-rule-id");
        current.setCurrentSeq(42);
        current.setSeqDate("20260802");
        current.setCreatedAt(LocalDateTime.of(
                2026, 8, 1, 9, 30));
        EntityCodeRule request = rule("asset");
        request.setId("stale-rule-id");
        when(codeRuleMapper.findByEntityCode(
                "asset")).thenReturn(Optional.of(current));

        service.saveRule(request);

        ArgumentCaptor<EntityCodeRule> captor =
                ArgumentCaptor.forClass(EntityCodeRule.class);
        verify(codeRuleMapper).updateById(captor.capture());
        EntityCodeRule updated = captor.getValue();
        assertEquals("persisted-rule-id", updated.getId());
        assertEquals(42, updated.getCurrentSeq());
        assertEquals("20260802", updated.getSeqDate());
        assertEquals(current.getCreatedAt(), updated.getCreatedAt());
        assertNull(updated.getUpdatedAt());
        verify(codeRuleMapper, never()).insert(request);
    }

    @Test
    void getRuleUsesNormalizedExistingEntityCode() {
        EntityCodeRule current = rule("asset");
        when(codeRuleMapper.findByEntityCode(
                "asset")).thenReturn(Optional.of(current));

        EntityCodeRule result = service.getRule("  asset  ");

        verify(entityAccessPolicy).requireDynamicByCode("asset");
        assertEquals(current, result);
    }

    private EntityCodeRule rule(String entityCode) {
        EntityCodeRule rule = new EntityCodeRule();
        rule.setEntityCode(entityCode);
        rule.setPrefix("AST");
        rule.setDateFormat("yyyyMMdd");
        rule.setSeqLength(4);
        rule.setSeqType(EntityCodeRule.SeqType.DAY.name());
        return rule;
    }
}
