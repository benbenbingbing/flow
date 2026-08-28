package com.workflow.entity.form.uniqueness.application;

import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.entity.form.application.PublishedFormUniqueRuleService;
import com.workflow.entity.form.application.model.FormUniqueCandidate;
import com.workflow.entity.form.application.model.FormUniqueCheck;
import com.workflow.entity.form.application.model.FormUniqueRule;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueClaimRepository;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueValueGateRepository;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueValueGateRepository.GateKey;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.record.EntityFormUniqueClaim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityFormUniqueClaimServiceTest {

    @Mock
    private PublishedFormUniqueRuleService ruleService;
    @Mock
    private EntityFormUniqueClaimRepository repository;
    @Mock
    private EntityFormUniqueValueGateRepository gateRepository;

    private EntityFormUniqueClaimService service;

    @BeforeEach
    void setUp() {
        service = new EntityFormUniqueClaimService(
                ruleService,
                repository,
                gateRepository);
    }

    @Test
    void nonFormMutationOnlyClearsStaleClaims() {
        EntityMutationCommand command = command(
                EntityMutationOperationType.UPDATE,
                EntityMutationSourceType.PROCESS_RUNTIME,
                Map.of());

        reconcile(command, Map.of(
                "name", "项目A",
                "status", "IN_PROGRESS"));

        verify(repository).releaseRecord(
                "project", "record-1");
        verify(ruleService, never()).resolveRules(
                eq("form-1"),
                eq("release-1"),
                eq(3),
                eq("release-1"),
                eq(null),
                eq(null));
        verify(repository, never()).reconcile(
                eq("project"),
                eq("record-1"),
                org.mockito.ArgumentMatchers.anyList());
        verify(gateRepository, never()).lockAll(
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void formWithoutRulesDoesNotValidateOrReserve() {
        EntityMutationCommand command = command(
                EntityMutationOperationType.UPDATE,
                EntityMutationSourceType.FORM,
                formContext());
        when(ruleService.resolveRules(
                "form-1",
                "release-1",
                3,
                "release-1",
                null,
                null))
                .thenReturn(List.of());

        reconcile(command, Map.of("name", "项目A"));

        verify(repository).releaseRecord(
                "project", "record-1");
        verify(repository, never()).reconcile(
                eq("project"),
                eq("record-1"),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void configuredFormReservesApplicableFinalValue() {
        EntityMutationCommand command = command(
                EntityMutationOperationType.STATUS_CHANGE,
                EntityMutationSourceType.FORM,
                formContext());
        FormUniqueRule rule = rule();
        Map<String, Object> finalRecord = Map.of(
                "name", " 项目A ",
                "status", "IN_PROGRESS");
        when(ruleService.resolveRules(
                "form-1",
                "release-1",
                3,
                "release-1",
                null,
                null))
                .thenReturn(List.of(rule));
        when(ruleService.check(
                rule,
                "project",
                "record-1",
                finalRecord)).thenReturn(new FormUniqueCheck(
                        true,
                        true,
                        rule.ruleId(),
                        rule.fieldCode(),
                        null));
        when(ruleService.candidate(rule, finalRecord))
                .thenReturn(new FormUniqueCandidate(
                        true,
                        false,
                        "项目a",
                        finalRecord));

        reconcile(command, finalRecord);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EntityFormUniqueClaim>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(repository).reconcile(
                eq("project"),
                eq("record-1"),
                captor.capture());
        EntityFormUniqueClaim claim = captor.getValue().get(0);
        assertEquals(
                "FORM:form-1:release-1:uq_name",
                claim.getConstraintKey());
        assertEquals(
                EntityFormUniqueClaimService.sha256("项目a"),
                claim.getValueHash());
        assertEquals("release-1", claim.getReleaseId());
        assertEquals(3, claim.getReleaseVersion());
        assertEquals(
                "release-1",
                claim.getEffectiveReleaseId());
        assertEquals(
                "项目名称在当前状态下已存在",
                claim.getConflictMessage());
    }

    @Test
    void rootDtoDataIsFlattenedBeforeRuleEvaluation() {
        EntityMutationCommand command = command(
                EntityMutationOperationType.UPDATE,
                EntityMutationSourceType.FORM,
                formContext());
        FormUniqueRule rule = rule();
        Map<String, Object> dtoRecord = Map.of(
                "id", "record-1",
                "status", "IN_PROGRESS",
                "data", Map.of("name", "项目A"));
        Map<String, Object> flattened = Map.of(
                "id", "record-1",
                "status", "IN_PROGRESS",
                "name", "项目A");
        when(ruleService.resolveRules(
                "form-1",
                "release-1",
                3,
                "release-1",
                null,
                null))
                .thenReturn(List.of(rule));
        when(ruleService.candidate(rule, flattened))
                .thenReturn(candidate("项目a", flattened));
        when(ruleService.check(
                rule,
                "project",
                "record-1",
                flattened)).thenReturn(available(rule));

        reconcile(command, dtoRecord);

        verify(ruleService, times(2)).candidate(rule, flattened);
        verify(ruleService).check(
                rule,
                "project",
                "record-1",
                flattened);
    }

    @Test
    void conditionFalseReconcilesToEmptyClaimSet() {
        EntityMutationCommand command = command(
                EntityMutationOperationType.UPDATE,
                EntityMutationSourceType.FORM,
                formContext());
        FormUniqueRule rule = rule();
        when(ruleService.resolveRules(
                "form-1",
                "release-1",
                3,
                "release-1",
                null,
                null))
                .thenReturn(List.of(rule));
        when(ruleService.candidate(
                eq(rule),
                anyMap())).thenReturn(new FormUniqueCandidate(
                        false,
                        false,
                        null,
                        Map.of("status", "DRAFT")));

        reconcile(command, Map.of("status", "DRAFT"));

        verify(repository).reconcile(
                "project",
                "record-1",
                List.of());
        verify(ruleService, never()).check(
                eq(rule),
                eq("project"),
                eq("record-1"),
                anyMap());
        verify(gateRepository, never()).lockAll(
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void committedConflictStopsBeforeReplacingClaims() {
        EntityMutationCommand command = command(
                EntityMutationOperationType.UPDATE,
                EntityMutationSourceType.FORM,
                formContext());
        FormUniqueRule rule = rule();
        Map<String, Object> finalRecord = Map.of(
                "name", "项目A",
                "status", "IN_PROGRESS");
        when(ruleService.resolveRules(
                "form-1",
                "release-1",
                3,
                "release-1",
                null,
                null))
                .thenReturn(List.of(rule));
        when(ruleService.candidate(rule, finalRecord))
                .thenReturn(candidate("\u9879\u76eea", finalRecord));
        when(ruleService.check(
                rule,
                "project",
                "record-1",
                finalRecord)).thenReturn(new FormUniqueCheck(
                        true,
                        false,
                        rule.ruleId(),
                        rule.fieldCode(),
                        rule.message()));

        com.workflow.core.error.BusinessConflictException exception =
                assertThrows(
                        com.workflow.core.error.BusinessConflictException.class,
                        () -> reconcile(
                                command,
                                finalRecord));

        assertEquals(
                EntityFormUniqueClaimRepository.CONFLICT_CODE,
                exception.getErrorCode());
        verify(repository, never()).reconcile(
                eq("project"),
                eq("record-1"),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void deleteOnlyReleasesClaims() {
        EntityMutationCommand command = command(
                EntityMutationOperationType.DELETE,
                EntityMutationSourceType.FORM,
                formContext());

        reconcile(command, Map.of());

        verify(repository).releaseRecord(
                "project", "record-1");
        verify(ruleService, never()).resolveRules(
                eq("form-1"),
                eq("release-1"),
                eq(3),
                eq("release-1"),
                eq(null),
                eq(null));
    }

    @Test
    void approvalTaskChecksEveryAppliedFormAndReconcilesClaimsOnce() {
        FormUniqueMutationContext.Reference firstReference =
                new FormUniqueMutationContext.Reference(
                        "form-1",
                        "release-1",
                        3,
                        "hotfix-1",
                        "hash-target-1",
                        "target-1");
        FormUniqueMutationContext.Reference secondReference =
                new FormUniqueMutationContext.Reference(
                        "form-2",
                        "release-2",
                        2,
                        "release-2");
        Map<String, Object> context = new java.util.LinkedHashMap<>();
        context.put("taskDefinitionKey", "Task_Review");
        context.putAll(FormUniqueMutationContext.encodeReferences(
                List.of(firstReference, secondReference)));
        EntityMutationCommand command = command(
                EntityMutationOperationType.UPDATE,
                EntityMutationSourceType.APPROVAL_TASK,
                context);
        FormUniqueRule firstRule = rule();
        FormUniqueRule secondRule = new FormUniqueRule(
                1,
                "uq_code",
                "code",
                "项目编码",
                "STRING",
                FormUniqueRule.Mode.GLOBAL,
                true,
                FormUniqueRule.Normalization.TRIM_CASE_INSENSITIVE,
                Map.of(),
                "项目编码已存在",
                firstRule.precheck());
        Map<String, Object> finalRecord = Map.of(
                "name", "项目A",
                "code", "P001",
                "status", "IN_PROGRESS");
        when(ruleService.resolveRules(
                "form-1",
                "release-1",
                3,
                "hotfix-1",
                "hash-target-1",
                "target-1"))
                .thenReturn(List.of(firstRule));
        when(ruleService.resolveRules(
                "form-2",
                "release-2",
                2,
                "release-2",
                null,
                null))
                .thenReturn(List.of(secondRule));
        when(ruleService.check(
                firstRule,
                "project",
                "record-1",
                finalRecord)).thenReturn(available(firstRule));
        when(ruleService.check(
                secondRule,
                "project",
                "record-1",
                finalRecord)).thenReturn(available(secondRule));
        when(ruleService.candidate(firstRule, finalRecord))
                .thenReturn(candidate("项目a", finalRecord));
        when(ruleService.candidate(secondRule, finalRecord))
                .thenReturn(candidate("p001", finalRecord));

        reconcile(command, finalRecord);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EntityFormUniqueClaim>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(repository).reconcile(
                eq("project"),
                eq("record-1"),
                captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(
                List.of(
                        "FORM:form-1:target-1:uq_name",
                        "FORM:form-2:release-2:uq_code"),
                captor.getValue().stream()
                        .map(EntityFormUniqueClaim::getConstraintKey)
                        .toList());
        verify(repository, never()).releaseRecord(
                "project", "record-1");
        EntityFormUniqueClaim hotfixClaim = captor.getValue().get(0);
        assertEquals("hotfix-1", hotfixClaim.getEffectiveReleaseId());
        assertEquals("hash-target-1", hotfixClaim.getEffectiveContentHash());
        assertEquals("target-1", hotfixClaim.getHotfixTargetId());
    }

    @Test
    void sameRuleIdUsesIndependentEffectiveReleaseNamespaces() {
        assertEquals(
                "FORM:form-1:release-1:uq_name",
                EntityFormUniqueClaimService.namespace(
                        "form-1", "release-1", "uq_name"));
        assertEquals(
                "FORM:form-1:release-2:uq_name",
                EntityFormUniqueClaimService.namespace(
                        "form-1", "release-2", "uq_name"));
        assertEquals(
                "ENTITY:project:name",
                EntityFormUniqueClaimService.stableScope(
                        "project", "name"));
    }

    @Test
    void crossReleaseChecksShareGateAndRunOnlyAfterItIsLocked() {
        FormUniqueMutationContext.Reference first =
                new FormUniqueMutationContext.Reference(
                        "form-1",
                        "release-1",
                        1,
                        "hotfix-2",
                        "hash-target-2",
                        "target-2");
        FormUniqueMutationContext.Reference second =
                new FormUniqueMutationContext.Reference(
                        "form-1", "release-3", 3, "release-3");
        EntityMutationCommand command = command(
                EntityMutationOperationType.UPDATE,
                EntityMutationSourceType.APPROVAL_TASK,
                FormUniqueMutationContext.encodeReferences(
                        List.of(first, second)));
        FormUniqueRule rule = rule();
        FormUniqueRule renamedRule = new FormUniqueRule(
                rule.version(),
                "uq_name_v2",
                rule.fieldCode(),
                rule.fieldLabel(),
                rule.fieldType(),
                rule.mode(),
                rule.ignoreBlank(),
                rule.normalization(),
                rule.condition(),
                rule.message(),
                rule.precheck());
        Map<String, Object> finalRecord = Map.of(
                "name", "\u9879\u76eeA",
                "status", "IN_PROGRESS");
        when(ruleService.resolveRules(
                "form-1",
                "release-1",
                1,
                "hotfix-2",
                "hash-target-2",
                "target-2"))
                .thenReturn(List.of(rule));
        when(ruleService.resolveRules(
                "form-1",
                "release-3",
                3,
                "release-3",
                null,
                null))
                .thenReturn(List.of(renamedRule));
        when(ruleService.candidate(rule, finalRecord))
                .thenReturn(candidate("\u9879\u76eea", finalRecord));
        when(ruleService.candidate(renamedRule, finalRecord))
                .thenReturn(candidate("\u9879\u76eea", finalRecord));
        when(ruleService.check(
                rule,
                "project",
                "record-1",
                finalRecord)).thenReturn(available(rule));
        when(ruleService.check(
                renamedRule,
                "project",
                "record-1",
                finalRecord)).thenReturn(available(renamedRule));

        reconcile(command, finalRecord);

        InOrder order = inOrder(
                gateRepository,
                ruleService,
                repository);
        order.verify(gateRepository).lockAll(
                gates("project", "name", "\u9879\u76eea"));
        order.verify(ruleService).check(
                rule,
                "project",
                "record-1",
                finalRecord);
        order.verify(ruleService).check(
                renamedRule,
                "project",
                "record-1",
                finalRecord);
        order.verify(repository).reconcile(
                eq("project"),
                eq("record-1"),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void differentFormsOnSameEntityFieldShareOneGateButKeepClaimsSeparate() {
        FormUniqueMutationContext.Reference first =
                new FormUniqueMutationContext.Reference(
                        "form-1", "release-1", 1, "release-1");
        FormUniqueMutationContext.Reference second =
                new FormUniqueMutationContext.Reference(
                        "form-2", "release-2", 2, "release-2");
        EntityMutationCommand command = command(
                EntityMutationOperationType.UPDATE,
                EntityMutationSourceType.APPROVAL_TASK,
                FormUniqueMutationContext.encodeReferences(
                        List.of(first, second)));
        FormUniqueRule firstRule = rule();
        FormUniqueRule secondRule = new FormUniqueRule(
                firstRule.version(),
                "uq_name_from_form_2",
                firstRule.fieldCode(),
                firstRule.fieldLabel(),
                firstRule.fieldType(),
                firstRule.mode(),
                firstRule.ignoreBlank(),
                firstRule.normalization(),
                firstRule.condition(),
                firstRule.message(),
                firstRule.precheck());
        Map<String, Object> finalRecord = Map.of(
                "name", "项目A",
                "status", "IN_PROGRESS");
        when(ruleService.resolveRules(
                "form-1", "release-1", 1,
                "release-1", null, null))
                .thenReturn(List.of(firstRule));
        when(ruleService.resolveRules(
                "form-2", "release-2", 2,
                "release-2", null, null))
                .thenReturn(List.of(secondRule));
        when(ruleService.candidate(firstRule, finalRecord))
                .thenReturn(candidate("项目a", finalRecord));
        when(ruleService.candidate(secondRule, finalRecord))
                .thenReturn(candidate("项目a", finalRecord));
        when(ruleService.check(
                firstRule, "project", "record-1", finalRecord))
                .thenReturn(available(firstRule));
        when(ruleService.check(
                secondRule, "project", "record-1", finalRecord))
                .thenReturn(available(secondRule));

        reconcile(command, finalRecord);

        verify(gateRepository).lockAll(
                gates("project", "name", "项目a"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EntityFormUniqueClaim>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(repository).reconcile(
                eq("project"), eq("record-1"), captor.capture());
        assertEquals(
                List.of(
                        "FORM:form-1:release-1:uq_name",
                        "FORM:form-2:release-2:uq_name_from_form_2"),
                captor.getValue().stream()
                        .map(EntityFormUniqueClaim::getConstraintKey)
                        .toList());
    }

    @Test
    void childFormWithoutRulesOnlyReleasesOldClaim() {
        FormUniqueMutationContext.Reference reference =
                new FormUniqueMutationContext.Reference(
                        "child-form",
                        "child-release-1",
                        1,
                        "child-hotfix-2",
                        "child-hash-2",
                        "child-target-2");
        when(ruleService.resolveRules(
                "child-form",
                "child-release-1",
                1,
                "child-hotfix-2",
                "child-hash-2",
                "child-target-2"))
                .thenReturn(List.of());

        var prepared = service.prepareAll(List.of(
                EntityFormUniqueClaimService.Preparation.of(
                        "project_line",
                        "line-1",
                        Map.of(),
                        Map.of("name", "\u660e\u7ec6A"),
                        List.of(reference)))).get(0);
        service.reconcileChildRecord(
                "project_line",
                "line-1",
                Map.of("name", "\u660e\u7ec6A"),
                List.of(reference),
                prepared);

        verify(repository).releaseRecord(
                "project_line", "line-1");
        verify(gateRepository, never()).lockAll(
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void nestedTrustedChildGateIsPreparedBeforeParentWrite() {
        FormUniqueMutationContext.Reference reference =
                new FormUniqueMutationContext.Reference(
                        "child-form",
                        "child-release-1",
                        1,
                        "child-release-1");
        FormUniqueRule rule = rule();
        Map<String, Object> child = new java.util.LinkedHashMap<>(
                Map.of(
                        "name", "明细A",
                        "status", "IN_PROGRESS"));
        TrustedSubFormUniqueReference.attach(
                child,
                "project_line",
                reference);
        when(ruleService.resolveRules(
                "child-form",
                "child-release-1",
                1,
                "child-release-1",
                null,
                null)).thenReturn(List.of(rule));
        when(ruleService.candidate(
                eq(rule),
                org.mockito.ArgumentMatchers.argThat(record ->
                        "明细A".equals(record.get("name")))))
                .thenReturn(candidate("明细a", child));
        when(ruleService.check(
                eq(rule),
                eq("project_line"),
                isNull(),
                anyMap())).thenReturn(available(rule));

        service.prepareAll(List.of(
                EntityFormUniqueClaimService.Preparation.of(
                        "project",
                        null,
                        Map.of(),
                        Map.of("details", List.of(child)),
                        List.of())));

        verify(gateRepository).lockAll(
                gates("project_line", "name", "明细a"));
        TrustedSubFormUniqueReference.Resolved resolved =
                TrustedSubFormUniqueReference.removePrepared(child);
        assertEquals("project_line", resolved.entityCode());
        assertEquals(List.of(reference), resolved.references());
        assertNotNull(resolved.prepared());
    }

    @Test
    void recursiveSelfChildPlanFailsClosedWhenNestedMarkerIsStripped() {
        FormUniqueMutationContext.Reference reference =
                new FormUniqueMutationContext.Reference(
                        "child-form",
                        "child-release-1",
                        1,
                        "child-release-1");
        FormUniqueRule rule = rule();
        Map<String, Object> grandchild = new java.util.LinkedHashMap<>(
                Map.of(
                        "name", "孙明细B",
                        "status", "IN_PROGRESS"));
        TrustedSubFormUniqueReference.attach(
                grandchild,
                "project_line",
                reference);
        Map<String, Object> child = new java.util.LinkedHashMap<>();
        child.put("name", "子明细A");
        child.put("status", "IN_PROGRESS");
        child.put("details", List.of(grandchild));
        TrustedSubFormUniqueReference.attach(
                child,
                "project_line",
                reference);
        Map<String, Object> rootPayload =
                new java.util.LinkedHashMap<>(Map.of(
                        "details", List.of(child)));
        when(ruleService.resolveRules(
                "child-form",
                "child-release-1",
                1,
                "child-release-1",
                null,
                null)).thenReturn(List.of(rule));
        when(ruleService.candidate(eq(rule), anyMap()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> record = invocation.getArgument(1);
                    String value = String.valueOf(record.get("name"))
                            .toLowerCase(java.util.Locale.ROOT);
                    return candidate(value, record);
                });
        when(ruleService.check(
                eq(rule),
                eq("project_line"),
                isNull(),
                anyMap())).thenReturn(available(rule));
        EntityFormUniqueClaimService.PreparedUniqueClaims prepared =
                service.prepareAll(List.of(
                        EntityFormUniqueClaimService.Preparation.of(
                                "project",
                                "record-1",
                                Map.of(),
                                rootPayload,
                                List.of()))).get(0);
        EntityMutationCommand rootCommand = new EntityMutationCommand(
                "operation-recursive",
                "project",
                "record-1",
                EntityMutationOperationType.UPDATE,
                rootPayload,
                EntityMutationContext.builder(
                                EntityMutationSourceType.FORM,
                                "TEST",
                                "递归子表单测试")
                        .build());

        service.verifyPrepared(rootCommand, Map.of(), prepared);
        TrustedSubFormUniqueReference.removePrepared(grandchild);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.verifyPrepared(
                        rootCommand,
                        Map.of(),
                        prepared));
        assertEquals(
                "可信子表单写计划与写入 payload 不一致",
                exception.getMessage());
    }

    @Test
    void recursiveChildPlanRejectsPreparedTokenFromAnotherRoot() {
        FormUniqueMutationContext.Reference reference =
                new FormUniqueMutationContext.Reference(
                        "child-form",
                        "child-release-1",
                        1,
                        "child-release-1");
        FormUniqueRule rule = rule();
        Map<String, Object> firstChild = new java.util.LinkedHashMap<>(
                Map.of("name", "明细A", "status", "IN_PROGRESS"));
        Map<String, Object> secondChild = new java.util.LinkedHashMap<>(
                Map.of("name", "明细B", "status", "IN_PROGRESS"));
        TrustedSubFormUniqueReference.attach(
                firstChild, "project_line", reference);
        TrustedSubFormUniqueReference.attach(
                secondChild, "project_line", reference);
        List<Map<String, Object>> firstRows =
                new java.util.ArrayList<>(List.of(firstChild));
        List<Map<String, Object>> secondRows =
                new java.util.ArrayList<>(List.of(secondChild));
        Map<String, Object> firstPayload = new java.util.LinkedHashMap<>(
                Map.of("details", firstRows));
        Map<String, Object> secondPayload = new java.util.LinkedHashMap<>(
                Map.of("details", secondRows));
        when(ruleService.resolveRules(
                "child-form", "child-release-1", 1,
                "child-release-1", null, null))
                .thenReturn(List.of(rule));
        when(ruleService.candidate(eq(rule), anyMap()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> record = invocation.getArgument(1);
                    return candidate(
                            String.valueOf(record.get("name"))
                                    .toLowerCase(java.util.Locale.ROOT),
                            record);
                });
        when(ruleService.check(
                eq(rule), eq("project_line"), isNull(), anyMap()))
                .thenReturn(available(rule));
        List<EntityFormUniqueClaimService.PreparedUniqueClaims> prepared =
                service.prepareAll(List.of(
                        EntityFormUniqueClaimService.Preparation.of(
                                "project", "root-1", Map.of(),
                                firstPayload, List.of()),
                        EntityFormUniqueClaimService.Preparation.of(
                                "project", "root-2", Map.of(),
                                secondPayload, List.of())));
        EntityMutationCommand firstCommand = new EntityMutationCommand(
                "operation-root-1",
                "project",
                "root-1",
                EntityMutationOperationType.UPDATE,
                firstPayload,
                EntityMutationContext.builder(
                                EntityMutationSourceType.FORM,
                                "TEST",
                                "token replacement")
                        .build());

        firstRows.set(0, secondChild);
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.verifyPrepared(
                        firstCommand,
                        Map.of(),
                        prepared.get(0)));

        assertEquals(
                "可信子表单 Marker 或写前 token 被剥离或替换",
                exception.getMessage());
    }

    @Test
    void postWriteCandidateDriftFailsClosedWithoutLateGate() {
        EntityMutationCommand command = command(
                EntityMutationOperationType.UPDATE,
                EntityMutationSourceType.FORM,
                formContext());
        FormUniqueRule rule = rule();
        Map<String, Object> beforeWrite = Map.of(
                "name", "项目A",
                "status", "IN_PROGRESS");
        Map<String, Object> afterWrite = Map.of(
                "name", "项目B",
                "status", "IN_PROGRESS");
        when(ruleService.resolveRules(
                "form-1", "release-1", 3,
                "release-1", null, null))
                .thenReturn(List.of(rule));
        when(ruleService.candidate(rule, beforeWrite))
                .thenReturn(candidate("项目a", beforeWrite));
        when(ruleService.candidate(rule, afterWrite))
                .thenReturn(candidate("项目b", afterWrite));
        when(ruleService.check(
                rule,
                "project",
                "record-1",
                beforeWrite)).thenReturn(available(rule));
        EntityFormUniqueClaimService.PreparedUniqueClaims prepared =
                service.prepareAll(List.of(
                        EntityFormUniqueClaimService.Preparation.of(
                                "project",
                                "record-1",
                                Map.of(),
                                beforeWrite,
                                FormUniqueMutationContext.resolveAll(
                                        command.context())))).get(0);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.reconcile(
                        command,
                        afterWrite,
                        prepared));

        assertEquals(
                "表单唯一候选在写前 gate 后发生漂移",
                exception.getMessage());
        verify(gateRepository, times(1)).lockAll(
                org.mockito.ArgumentMatchers.anyList());
        // 权威查询只在写前 gate 后执行一次，写后漂移不会触发第二次查询或补锁。
        verify(ruleService, times(1)).check(
                eq(rule), eq("project"), eq("record-1"), anyMap());
        verify(repository, never()).reconcile(
                eq("project"), eq("record-1"),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void crossFormBatchDuplicateCandidatesFailBeforeAnyBusinessWrite() {
        FormUniqueMutationContext.Reference firstReference =
                FormUniqueMutationContext.resolveAll(
                        command(
                                EntityMutationOperationType.UPDATE,
                                EntityMutationSourceType.FORM,
                                formContext()).context()).get(0);
        FormUniqueMutationContext.Reference secondReference =
                new FormUniqueMutationContext.Reference(
                        "form-2", "release-2", 2, "release-2");
        FormUniqueRule firstRule = rule();
        FormUniqueRule secondRule = new FormUniqueRule(
                firstRule.version(),
                "uq_name_form_2",
                firstRule.fieldCode(),
                firstRule.fieldLabel(),
                firstRule.fieldType(),
                firstRule.mode(),
                firstRule.ignoreBlank(),
                firstRule.normalization(),
                firstRule.condition(),
                firstRule.message(),
                firstRule.precheck());
        Map<String, Object> first = Map.of(
                "name", "项目A", "status", "IN_PROGRESS");
        Map<String, Object> second = Map.of(
                "name", " 项目A ", "status", "IN_PROGRESS");
        when(ruleService.resolveRules(
                "form-1", "release-1", 3,
                "release-1", null, null))
                .thenReturn(List.of(firstRule));
        when(ruleService.resolveRules(
                "form-2", "release-2", 2,
                "release-2", null, null))
                .thenReturn(List.of(secondRule));
        when(ruleService.candidate(firstRule, first))
                .thenReturn(candidate("项目a", first));
        when(ruleService.candidate(firstRule, second))
                .thenReturn(candidate("项目a", second));
        when(ruleService.candidate(secondRule, second))
                .thenReturn(candidate("项目a", second));

        assertThrows(
                com.workflow.core.error.BusinessConflictException.class,
                () -> service.prepareAll(List.of(
                        EntityFormUniqueClaimService.Preparation.of(
                                "project", "record-1",
                                Map.of(), first,
                                List.of(firstReference)),
                        EntityFormUniqueClaimService.Preparation.of(
                                "project", "record-2",
                                Map.of(), second,
                                List.of(secondReference)))));

        verify(gateRepository).lockAll(
                gates("project", "name", "项目a"));
        verify(ruleService, never()).check(
                org.mockito.ArgumentMatchers.any(), eq("project"),
                org.mockito.ArgumentMatchers.any(), anyMap());
    }

    @Test
    void differentValuesRunExclusiveScansInGlobalGateOrder() {
        FormUniqueMutationContext.Reference reference =
                FormUniqueMutationContext.resolveAll(
                        command(
                                EntityMutationOperationType.UPDATE,
                                EntityMutationSourceType.FORM,
                                formContext()).context()).get(0);
        FormUniqueRule rule = rule();
        Map<String, Object> first = Map.of(
                "name", "项目A", "status", "IN_PROGRESS");
        Map<String, Object> second = Map.of(
                "name", "项目B", "status", "IN_PROGRESS");
        when(ruleService.resolveRules(
                "form-1", "release-1", 3,
                "release-1", null, null))
                .thenReturn(List.of(rule));
        when(ruleService.candidate(rule, first))
                .thenReturn(candidate("项目a", first));
        when(ruleService.candidate(rule, second))
                .thenReturn(candidate("项目b", second));
        when(ruleService.check(
                rule, "project", "record-1", first))
                .thenReturn(available(rule));
        when(ruleService.check(
                rule, "project", "record-2", second))
                .thenReturn(available(rule));

        service.prepareAll(List.of(
                EntityFormUniqueClaimService.Preparation.of(
                        "project", "record-2",
                        Map.of(), second, List.of(reference)),
                EntityFormUniqueClaimService.Preparation.of(
                        "project", "record-1",
                        Map.of(), first, List.of(reference))));

        GateKey firstGate = new GateKey(
                "ENTITY:project:name",
                EntityFormUniqueClaimService.sha256("项目a"));
        GateKey secondGate = new GateKey(
                "ENTITY:project:name",
                EntityFormUniqueClaimService.sha256("项目b"));
        List<GateKey> valueOrder = java.util.stream.Stream.of(
                        firstGate, secondGate)
                .sorted()
                .toList();
        List<GateKey> ordered = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                EntityFormUniqueClaimService
                                        .fieldSentinelGate(
                                                "project", "name")),
                        valueOrder.stream())
                .distinct()
                .sorted()
                .toList();
        InOrder order = inOrder(gateRepository, ruleService);
        order.verify(gateRepository).lockAll(ordered);
        for (GateKey gate : valueOrder) {
            if (gate.equals(firstGate)) {
                order.verify(ruleService).check(
                        rule, "project", "record-1", first);
            } else {
                order.verify(ruleService).check(
                        rule, "project", "record-2", second);
            }
        }
    }

    /**
     * 条件规则 A/B swap 威胁模型：两个事务写不同 normalized value，权威查询
     * 又可能在应用层按条件排除对方旧行。两次准备仍必须先竞争同一字段 sentinel，
     * 因此第二次扫描不能与第一次扫描/写入窗口并发穿越。
     */
    @Test
    void conditionalRuleSwapValuesShareSentinelBeforeEitherScan() {
        FormUniqueMutationContext.Reference reference =
                FormUniqueMutationContext.resolveAll(
                        command(
                                EntityMutationOperationType.UPDATE,
                                EntityMutationSourceType.FORM,
                                formContext()).context()).get(0);
        FormUniqueRule conditional = rule();
        Map<String, Object> swapToB = Map.of(
                "name", "项目B",
                "status", "IN_PROGRESS",
                "lane", "RED");
        Map<String, Object> swapToA = Map.of(
                "name", "项目A",
                "status", "IN_PROGRESS",
                "lane", "BLUE");
        when(ruleService.resolveRules(
                "form-1", "release-1", 3,
                "release-1", null, null))
                .thenReturn(List.of(conditional));
        when(ruleService.candidate(conditional, swapToB))
                .thenReturn(candidate("项目b", swapToB));
        when(ruleService.candidate(conditional, swapToA))
                .thenReturn(candidate("项目a", swapToA));
        // available 模拟 conflict query 找到对方行后又被条件规则在应用层排除。
        when(ruleService.check(
                conditional, "project", "record-1", swapToB))
                .thenReturn(available(conditional));
        when(ruleService.check(
                conditional, "project", "record-2", swapToA))
                .thenReturn(available(conditional));

        service.prepareAll(List.of(
                EntityFormUniqueClaimService.Preparation.of(
                        "project", "record-1", Map.of(),
                        swapToB, List.of(reference))));
        service.prepareAll(List.of(
                EntityFormUniqueClaimService.Preparation.of(
                        "project", "record-2", Map.of(),
                        swapToA, List.of(reference))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GateKey>> gates =
                ArgumentCaptor.forClass(List.class);
        verify(gateRepository, times(2)).lockAll(gates.capture());
        GateKey sentinel = EntityFormUniqueClaimService
                .fieldSentinelGate("project", "name");
        assertEquals(true,
                gates.getAllValues().get(0).contains(sentinel));
        assertEquals(true,
                gates.getAllValues().get(1).contains(sentinel));
        assertEquals(
                gates("project", "name", "项目b"),
                gates.getAllValues().get(0));
        assertEquals(
                gates("project", "name", "项目a"),
                gates.getAllValues().get(1));
        InOrder order = inOrder(gateRepository, ruleService);
        order.verify(gateRepository).lockAll(
                gates("project", "name", "项目b"));
        order.verify(ruleService).check(
                conditional, "project", "record-1", swapToB);
        order.verify(gateRepository).lockAll(
                gates("project", "name", "项目a"));
        order.verify(ruleService).check(
                conditional, "project", "record-2", swapToA);
    }

    private EntityMutationCommand command(
            EntityMutationOperationType operationType,
            EntityMutationSourceType sourceType,
            Map<String, Object> context) {
        return new EntityMutationCommand(
                "operation-1",
                "project",
                "record-1",
                operationType,
                Map.of(),
                EntityMutationContext.builder(
                                sourceType,
                                "TEST",
                                "测试")
                        .sourceId(sourceType
                                == EntityMutationSourceType.FORM
                                ? "form-1" : null)
                        .extraParams(context)
                        .build());
    }

    private void reconcile(
            EntityMutationCommand command,
            Map<String, Object> finalRecord) {
        EntityFormUniqueClaimService.PreparedUniqueClaims prepared;
        if (command.operationType()
                == EntityMutationOperationType.DELETE) {
            prepared = service.prepare(command, Map.of());
        } else {
            prepared = service.prepareAll(List.of(
                    EntityFormUniqueClaimService.Preparation.of(
                            command.entityCode(),
                            command.recordId(),
                            Map.of(),
                            finalRecord,
                            FormUniqueMutationContext.resolveAll(
                                    command.context())))).get(0);
        }
        service.reconcile(command, finalRecord, prepared);
    }

    private Map<String, Object> formContext() {
        return Map.of(
                FormUniqueMutationContext.FORM_ID,
                "form-1",
                FormUniqueMutationContext.FORM_RELEASE_ID,
                "release-1",
                FormUniqueMutationContext.FORM_RELEASE_VERSION,
                3);
    }

    private FormUniqueRule rule() {
        return new FormUniqueRule(
                1,
                "uq_name",
                "name",
                "项目名称",
                "STRING",
                FormUniqueRule.Mode.CONDITIONAL,
                true,
                FormUniqueRule.Normalization
                        .TRIM_CASE_INSENSITIVE,
                Map.of("version", 1),
                "项目名称在当前状态下已存在",
                new FormUniqueRule.Precheck(
                        true,
                        FormUniqueRule.Trigger.CHANGE,
                        500,
                        true));
    }

    private FormUniqueCheck available(FormUniqueRule rule) {
        return new FormUniqueCheck(
                true,
                true,
                rule.ruleId(),
                rule.fieldCode(),
                null);
    }

    private FormUniqueCandidate candidate(
            String value,
            Map<String, Object> record) {
        return new FormUniqueCandidate(
                true,
                false,
                value,
                record);
    }

    /** 适用候选同时持有字段 sentinel 与规范化 value gate。 */
    private List<GateKey> gates(
            String entityCode,
            String fieldCode,
            String... normalizedValues) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                EntityFormUniqueClaimService
                                        .fieldSentinelGate(
                                                entityCode,
                                                fieldCode)),
                        java.util.Arrays.stream(normalizedValues)
                                .map(value -> new GateKey(
                                        EntityFormUniqueClaimService
                                                .stableScope(
                                                        entityCode,
                                                        fieldCode),
                                        EntityFormUniqueClaimService
                                                .sha256(value))))
                .distinct()
                .sorted()
                .toList();
    }
}
