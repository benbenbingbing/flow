package com.workflow.entity.form.uniqueness.infrastructure.persistence;

import com.workflow.entity.form.uniqueness.infrastructure.persistence.mapper.EntityFormUniqueValueGateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * 在稳定的实体字段作用域内锁定字段 sentinel 与具体唯一值 gate。
 *
 * <p>占位命名空间包含 effective release 以隔离不同版本规则；值门闩故意
 * 不包含表单或发布版。当前入口中实际适用的规则先共享字段 sentinel，避免
 * 条件查询在不同 value 间交叉锁行；具体 value gate 再保护同值窗口。锁行
 * 不删除，避免删除与重建之间出现 ABA 窗口。</p>
 */
@Repository
@RequiredArgsConstructor
public class EntityFormUniqueValueGateRepository {

    private final EntityFormUniqueValueGateMapper mapper;

    /**
     * 按稳定键顺序锁定全部字段 sentinel/value gate，避免多规则事务反序互锁。
     *
     * @param keys 由稳定字段作用域与 sentinel/规范化值哈希组成的锁键
     */
    public void lockAll(List<GateKey> keys) {
        List<GateKey> ordered = (keys == null
                ? List.<GateKey>of() : keys).stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        for (GateKey key : ordered) {
            mapper.insertIgnore(
                    key.scopeKey(),
                    key.valueHash());
            String locked = mapper.lockForUpdate(
                    key.scopeKey(),
                    key.valueHash());
            if (!key.valueHash().equals(locked)) {
                throw new IllegalStateException(
                        "表单唯一值门闩锁定失败");
            }
        }
    }

    /** 稳定字段作用域与 sentinel 或规范化值的事务锁键。 */
    public record GateKey(
            String scopeKey,
            String valueHash)
            implements Comparable<GateKey> {

        public GateKey {
            require(scopeKey, "唯一值门闩作用域不能为空");
            require(valueHash, "唯一值门闩哈希不能为空");
        }

        @Override
        public int compareTo(GateKey other) {
            int scopeOrder = scopeKey.compareTo(
                    other.scopeKey);
            return scopeOrder != 0
                    ? scopeOrder
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
