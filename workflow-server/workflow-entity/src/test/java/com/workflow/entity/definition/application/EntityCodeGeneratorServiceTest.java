package com.workflow.entity.definition.application;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityCodeRuleMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityCodeRule;
import com.workflow.entity.definition.application.code.*;
import com.workflow.entity.data.application.EntityCodeReservationService;
import com.workflow.contracts.entity.code.*;
import com.workflow.contracts.entity.code.spi.EntityCodeGeneratorProvider;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.application.validation.UiExtensionDefinitionValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
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

    private RuleEntityCodeGenerator ruleGenerator;
    @Mock private EntityCodeReservationService reservations;
    @Mock private EntityCodeContextFactory contexts;
    private EntityCodeGeneratorService service;

    private EntityCodeGeneratorRegistry registry(EntityCodeGeneratorProvider... generators) {
        return new EntityCodeGeneratorRegistry(List.of(generators), new UiExtensionDefinitionValidator(new JsonDocumentCodec(new ObjectMapper())));
    }

    private void use(EntityCodeGeneratorProvider generator) {
        service = new EntityCodeGeneratorService(codeRuleMapper, entityAccessPolicy, ruleGenerator,
                registry(generator), reservations, contexts);
    }

    @BeforeEach
    void setUp() {
        ruleGenerator = spy(new RuleEntityCodeGenerator(codeRuleMapper));
        service = new EntityCodeGeneratorService(
                codeRuleMapper,
                entityAccessPolicy, ruleGenerator, registry(), reservations, contexts);
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
        verify(codeRuleMapper, never()).updateConfiguration(rule);
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
        verify(codeRuleMapper).updateConfiguration(captor.capture());
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

    @Test
    void existingApiFormatsAndShortSequencesRemainSupported() {
        EntityCodeRule rule = rule("asset");
        rule.setDateFormat("yyyy"); rule.setSeqType("YEAR"); rule.setSeqLength(2);
        assertEquals("AST" + java.time.LocalDate.now().getYear() + "01", service.previewCode(rule));
    }

    @Test
    void customReceivesUnpersistedIdentityAndIgnoresSuppliedChildCode() {
        var calls = new AtomicInteger();
        use(generator("PROJECT", calls, "XM-001", Optional.empty()));
        EntityCodeRule rule = rule("asset");
        rule.setGenerationMode("CUSTOM"); rule.setGeneratorCode("PROJECT");
        when(codeRuleMapper.findByEntityCode("asset")).thenReturn(Optional.of(rule));
        var context = context();
        assertEquals("XM-001", service.generateCode(context, "CLIENT-BYPASS"));
        assertEquals(1, calls.get());
        verify(reservations).reserve("asset", "record-1", "XM-001");
        verifyNoInteractions(ruleGenerator);
    }

    @Test
    void previewAndConfigurationSaveNeverAllocateNumbers() {
        var calls = new AtomicInteger();
        use(generator("PROJECT", calls, "XM-001", Optional.empty()));
        EntityCodeRule rule = rule("asset");
        rule.setGenerationMode("CUSTOM"); rule.setGeneratorCode("PROJECT");
        assertEquals("", service.previewCode(rule));
        service.saveRule(rule);
        assertEquals(0, calls.get());
        verify(reservations, never()).reserve(any(), any(), any());
        verifyNoInteractions(ruleGenerator);
    }

    @Test
    void missingOrInvalidCustomImplementationFailsWithoutFallback() {
        EntityCodeRule rule = rule("asset");
        rule.setGenerationMode("CUSTOM"); rule.setGeneratorCode("MISSING");
        when(codeRuleMapper.findByEntityCode("asset")).thenReturn(Optional.of(rule));
        assertThrows(IllegalArgumentException.class, () -> service.generateCode(context(), null));
        verifyNoInteractions(ruleGenerator, reservations);
        for (String invalid : List.of("", " ", "x".repeat(101), "prefix\nvalue", " trailing ")) {
            use(generator("MISSING", new AtomicInteger(), invalid, Optional.empty()));
            assertThrows(com.workflow.core.error.BusinessConflictException.class, () -> service.generateCode(context(), null));
        }
        verifyNoInteractions(reservations);
    }

    @Test
    void legacyRuleUsesIndependentSequencerAndReservesReturnedCode() {
        EntityCodeRule rule = rule("asset");
        when(codeRuleMapper.findByEntityCode("asset")).thenReturn(Optional.of(rule));
        doReturn("AST001").when(ruleGenerator).generateCode("asset", rule);
        assertEquals("AST001", service.generateCode(context(), null));
        verify(reservations).reserve("asset", "record-1", "AST001");
    }

    @Test
    void registryRejectsDuplicateNamesAndWrongEntityOrConfiguration() {
        var generator = generator("PROJECT", new AtomicInteger(), "XM-001", Optional.empty());
        assertThrows(IllegalStateException.class, () -> registry(generator, generator));
        EntityCodeGeneratorProvider restricted = new EntityCodeGeneratorProvider() {
            public String getCode() { return "SCOPED"; }
            public String getDisplayName() { return "Scoped"; }
            public Set<String> supportedEntityCodes() { return Set.of("asset"); }
            public Map<String, Object> configurationSchema() {
                return Map.of("type", "object", "required", List.of("prefix"),
                        "properties", Map.of("prefix", Map.of("type", "string")));
            }
            public String generate(EntityCodeGenerationContext c, Map<String, Object> config) { return "x"; }
        };
        var registry = registry(restricted);
        assertTrue(registry.options("other").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> registry.require("SCOPED", "other", Map.of("prefix", "x")));
        assertThrows(IllegalArgumentException.class, () -> registry.require("SCOPED", "asset", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> registry.require("SCOPED", "asset", Map.of("prefix", 1)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void businessSnapshotCannotBeMutatedThroughNestedValues() {
        var original = new java.util.LinkedHashMap<String, Object>();
        original.put("items", new java.util.ArrayList<>(List.of(new java.util.LinkedHashMap<>(Map.of("type", "A")))));
        var context = new EntityCodeGenerationContext("asset", "record-1", original, "actor", null, null, null, null, LocalDateTime.now(), null);
        assertThrows(UnsupportedOperationException.class, () -> context.data().put("hacked", true));
        var list = (List<Map<String, Object>>) context.data().get("items");
        assertThrows(UnsupportedOperationException.class, () -> list.get(0).put("type", "B"));
        original.clear();
        assertEquals("A", list.get(0).get("type"));
    }

    private EntityCodeGenerationContext context() {
        return new EntityCodeGenerationContext("asset", "record-1", Map.of("projectType", "A"),
                "actor", "dept", null, null, Map.of(), LocalDateTime.now(), "request:root");
    }

    private EntityCodeGeneratorProvider generator(String code, AtomicInteger calls, String result, Optional<String> preview) {
        return new EntityCodeGeneratorProvider() {
            public String getCode() { return code; }
            public String getDisplayName() { return code; }
            public String generate(EntityCodeGenerationContext context, Map<String, Object> config) {
                calls.incrementAndGet();
                assertEquals("record-1", context.recordId());
                assertEquals("A", context.data().get("projectType"));
                return result;
            }
            public Optional<String> preview(EntityCodePreviewContext context, Map<String, Object> config) { return preview; }
        };
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
