package com.workflow.entity.form.uniqueness.infrastructure.persistence;

import com.workflow.entity.form.uniqueness.infrastructure.persistence.mapper.EntityFormUniqueValueGateMapper;
import lombok.RequiredArgsConstructor;
import com.workflow.core.database.JdbcLockedRow;
import java.util.Map;
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
    private final JdbcLockedRow lockedRows;

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
            lockedRows.ensureAndLock("entity_form_unique_value_gate",
                    Map.of("scope_key", key.scopeKey(), "value_hash", key.valueHash()),
                    List.of("scope_key", "value_hash"));
            String locked = mapper.lockForUpdate(
                    key.scopeKey(),
                    key.valueHash());
            if (!key.valueHash().equals(locked)) {
                throw new IllegalStateException(
                        "表单唯一值门闩锁定失败");
            }
        }
    }

    /**
     * 稳定字段作用域与 sentinel 或规范化值的事务锁键。
     *
     * @param scopeKey 作用域键，后续用于授权校验、关联或幂等去重
     * @param valueHash 值哈希，保存在对象中供后续校验、查询或展示
     */
    public record GateKey(
            String scopeKey,
            String valueHash)
            implements Comparable<GateKey> {

        /**
         * 初始化{@code gate}键，保存构造参数供后续方法使用。
         *
         * @param scopeKey 作用域键，后续用于授权校验、关联或幂等去重
         * @param valueHash 值哈希，保存在对象中供后续校验、查询或展示
         */
        public GateKey {
            require(scopeKey, "唯一值门闩作用域不能为空");
            require(valueHash, "唯一值门闩哈希不能为空");
        }

        /**
         * 比较截止；结果供调用方的后续步骤使用。
         *
         * @param other {@code other}，供本方法比较截止时使用
         * @return 比较后的截止结果，供调用方继续处理
         */
        @Override
        public int compareTo(GateKey other) {
            int scopeOrder = scopeKey.compareTo(
                    other.scopeKey);
            return scopeOrder != 0
                    ? scopeOrder
                    : valueHash.compareTo(other.valueHash);
        }

        /**
         * 校验并获取{@code gate}键；不满足约束时阻止后续处理。
         *
         * @param value 待校验并获取{@code gate}键的原始输入，结果供调用方继续使用
         * @param message 消息，作为 {@code IllegalArgumentException} 的输入影响后续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private static void require(
                String value,
                String message) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(message);
            }
        }
    }
}
