package com.workflow.entity.form.uniqueness.infrastructure.persistence;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.mapper.EntityFormUniqueClaimMapper;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.record.EntityFormUniqueClaim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityFormUniqueClaimRepositoryTest {

    @Mock
    private EntityFormUniqueClaimMapper mapper;

    private EntityFormUniqueClaimRepository repository;

    @BeforeEach
    void setUp() {
        repository = new EntityFormUniqueClaimRepository(mapper);
    }

    @Test
    void releaseRecordClearsClaimsFromEveryFormWithoutValidatingRules() {
        repository.releaseRecord("project", "record-1");

        verify(mapper).deleteAllByRecord("project", "record-1");
    }

    @Test
    void reconcileReplacesAllRecordClaimsInStableOrder() {
        EntityFormUniqueClaim first = claim(
                "FORM:form-1:release-1:uq_code",
                "hash-a",
                "record-1");
        EntityFormUniqueClaim second = claim(
                "FORM:form-1:release-1:uq_name",
                "hash-b",
                "record-1");

        repository.reconcile(
                "project",
                "record-1",
                List.of(second, first));

        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteAllByRecord(
                "project", "record-1");
        order.verify(mapper).insert(first);
        order.verify(mapper).insert(second);
    }

    @Test
    void configuredFormBReplacementClearsClaimsPreviouslyOwnedByFormA() {
        EntityFormUniqueClaim formBClaim = claim(
                "FORM:form-b:release-b:uq_name",
                "hash-b",
                "record-1");
        formBClaim.setFormId("form-b");
        formBClaim.setEffectiveReleaseId("release-b");

        repository.reconcile(
                "project",
                "record-1",
                List.of(formBClaim));

        InOrder order = inOrder(mapper);
        // deleteAllByRecord 的范围刻意不含 form_id，防止 A 的旧占位残留。
        order.verify(mapper).deleteAllByRecord(
                "project", "record-1");
        order.verify(mapper).insert(formBClaim);
    }

    @Test
    void duplicatePrimaryKeyBecomesStableFieldConflict() {
        EntityFormUniqueClaim desired = claim(
                "FORM:form-1:release-1:uq_name",
                "hash-a",
                "record-1");
        desired.setConflictMessage("项目名称在当前状态下已存在");
        when(mapper.insert(desired)).thenThrow(
                new DuplicateKeyException("duplicate"));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> repository.reconcile(
                        "project",
                        "record-1",
                        List.of(desired)));

        assertEquals(
                EntityFormUniqueClaimRepository.CONFLICT_CODE,
                exception.getErrorCode());
        assertEquals(
                "项目名称在当前状态下已存在",
                exception.getMessage());
    }

    private EntityFormUniqueClaim claim(
            String constraintKey,
            String valueHash,
            String recordId) {
        EntityFormUniqueClaim claim = new EntityFormUniqueClaim();
        claim.setConstraintKey(constraintKey);
        claim.setValueHash(valueHash);
        claim.setEntityCode("project");
        claim.setFormId("form-1");
        claim.setRuleId(constraintKey.substring(
                constraintKey.lastIndexOf(':') + 1));
        claim.setFieldCode("name");
        claim.setNormalizedValue("项目a");
        claim.setRecordId(recordId);
        claim.setReleaseId("release-1");
        claim.setReleaseVersion(1);
        claim.setEffectiveReleaseId("release-1");
        return claim;
    }
}
