package com.workflow.entity.form.uniqueness.infrastructure.persistence;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.mapper.EntityFormUniqueClaimMapper;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.record.EntityFormUniqueClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表单唯一值占位仓储。
 *
 * <p>调用方必须位于业务数据写入事务内。本仓储先清理记录经由任意表单持有的旧
 * 占位，再按稳定顺序申请目标占位；若申请失败，业务事务回滚会同时恢复旧占位。
 * 数据库复合主键负责关闭已配置相同表单规则的并发请求竞态窗口。</p>
 */
@Repository
@RequiredArgsConstructor
public class EntityFormUniqueClaimRepository {

    public static final String CONFLICT_CODE =
            "FORM_FIELD_UNIQUE_CONFLICT";

    private final EntityFormUniqueClaimMapper mapper;

    /**
     * 清理一条记录经由任意表单持有的旧占位。
     *
     * <p>即使本次变更来自未配置规则的表单也必须执行清理，否则记录值或状态
     * 已改变后，旧表单留下的占位会继续误报冲突。清理本身不执行唯一校验。</p>
     */
    public void releaseRecord(
            String entityCode,
            String recordId) {
        requireText(entityCode, "实体编码不能为空");
        requireText(recordId, "唯一值占位必须提供记录ID");
        mapper.deleteAllByRecord(entityCode, recordId);
    }

    /**
     * 将当前记录由本次全部表单规则持有的占位原子协调为目标集合。
     *
     * @param entityCode 实体编码
     * @param recordId 业务记录ID
     * @param desiredClaims 根据最终记录计算出的目标占位
     */
    public void reconcile(
            String entityCode,
            String recordId,
            List<EntityFormUniqueClaim> desiredClaims) {
        requireText(entityCode, "实体编码不能为空");
        requireText(recordId, "唯一值占位必须提供记录ID");

        Map<ClaimKey, EntityFormUniqueClaim> desired = toMap(
                desiredClaims == null ? List.of() : desiredClaims);
        mapper.deleteAllByRecord(entityCode, recordId);
        for (Map.Entry<ClaimKey, EntityFormUniqueClaim> entry
                : desired.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList()) {
            EntityFormUniqueClaim claim = entry.getValue();
            validateOwnership(
                    claim,
                    entityCode,
                    recordId);
            try {
                mapper.insert(claim);
            } catch (DuplicateKeyException exception) {
                throw new BusinessConflictException(
                        CONFLICT_CODE,
                        conflictMessage(claim));
            }
        }
    }

    private Map<ClaimKey, EntityFormUniqueClaim> toMap(
            List<EntityFormUniqueClaim> claims) {
        Map<ClaimKey, EntityFormUniqueClaim> result =
                new LinkedHashMap<>();
        for (EntityFormUniqueClaim claim : claims) {
            if (claim == null) {
                continue;
            }
            ClaimKey key = new ClaimKey(
                    claim.getConstraintKey(),
                    claim.getValueHash());
            EntityFormUniqueClaim previous = result.putIfAbsent(
                    key,
                    claim);
            if (previous != null
                    && !Objects.equals(
                            previous.getRecordId(),
                            claim.getRecordId())) {
                throw new IllegalArgumentException(
                        "目标唯一值占位集合包含重复键");
            }
        }
        return result;
    }

    private void validateOwnership(
            EntityFormUniqueClaim claim,
            String entityCode,
            String recordId) {
        if (!Objects.equals(entityCode, claim.getEntityCode())
                || !Objects.equals(recordId, claim.getRecordId())) {
            throw new IllegalArgumentException(
                    "唯一值占位与当前实体或记录不匹配");
        }
        if (claim.getFormId() == null
                || claim.getFormId().isBlank()
                || claim.getEffectiveReleaseId() == null
                || claim.getEffectiveReleaseId().isBlank()) {
            throw new IllegalArgumentException(
                    "唯一值占位缺少表单或有效发布身份");
        }
        if (claim.getHotfixTargetId() != null
                && !claim.getHotfixTargetId().isBlank()
                && (claim.getEffectiveContentHash() == null
                || claim.getEffectiveContentHash().isBlank())) {
            throw new IllegalArgumentException(
                    "热修复唯一值占位缺少有效快照哈希");
        }
    }

    private String conflictMessage(EntityFormUniqueClaim claim) {
        if (claim.getConflictMessage() != null
                && !claim.getConflictMessage().isBlank()) {
            return claim.getConflictMessage();
        }
        return (claim.getFieldCode() == null
                || claim.getFieldCode().isBlank())
                ? "字段值已被其他记录占用"
                : "字段值已被其他记录占用: "
                        + claim.getFieldCode();
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private record ClaimKey(
            String constraintKey,
            String valueHash)
            implements Comparable<ClaimKey> {

        private ClaimKey {
            require(constraintKey, "唯一约束命名空间不能为空");
            require(valueHash, "唯一值哈希不能为空");
        }

        @Override
        public int compareTo(ClaimKey other) {
            int namespaceOrder = constraintKey.compareTo(
                    other.constraintKey);
            return namespaceOrder != 0
                    ? namespaceOrder
                    : valueHash.compareTo(other.valueHash);
        }

        private static void require(
                String value,
                String message) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(message);
            }
        }
    }
}
