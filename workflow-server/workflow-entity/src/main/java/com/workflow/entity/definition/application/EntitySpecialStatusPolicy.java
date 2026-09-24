package com.workflow.entity.definition.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import java.util.List;
import java.util.Set;

/** 特殊操作必须命中唯一目标；普通业务类别仍允许配置多个连线目标。 */
public final class EntitySpecialStatusPolicy {
    public static final Set<String> CATEGORIES = Set.of("TERMINATED", "WITHDRAWN");

    private EntitySpecialStatusPolicy() { }

    /** 保存/发布前校验全部候选配置，required 指当前启用的操作所必需的类别。 */
    public static void validate(List<EntityStatus> statuses, Set<String> required) {
        List<EntityStatus> values = statuses == null ? List.of() : statuses;
        for (String category : CATEGORIES) {
            long count = values.stream().filter(value -> category.equals(value.getStatusCategory())).count();
            if (count > 1 || (required.contains(category) && count != 1)) {
                throw invalid(category, count);
            }
        }
    }

    /**
     * 在操作前及状态回写时解析唯一目标，禁止从多条配置中取第一条或写入未配置的硬编码。
     * 抛出配置冲突会阻止取消/回写继续，供管理员修复存量配置后重试。
     */
    public static String requireTarget(String category, List<EntityStatus> matches) {
        if (!CATEGORIES.contains(category)) throw new IllegalArgumentException("不是特殊结束类别: " + category);
        int count = matches == null ? 0 : matches.size();
        if (count != 1) throw invalid(category, count);
        String code = matches.get(0).getStatusCode();
        if (code == null || code.isBlank()) throw new IllegalArgumentException("特殊结束状态编码不能为空");
        return code;
    }

    private static BusinessConflictException invalid(String category, long count) {
        String label = "WITHDRAWN".equals(category) ? "撤回" : "终止";
        return new BusinessConflictException("ENTITY_SPECIAL_STATUS_INVALID",
                label + "类状态必须唯一，当前配置 " + count + " 个，请先修正实体状态配置");
    }
}
