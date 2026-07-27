package com.workflow.entity.data.domain.policy;

/**
 * 流程结束时的实体状态保留规则。
 */
public final class EntityProcessStatusPolicy {

    private EntityProcessStatusPolicy() {
    }

    public static boolean shouldPreserve(String currentCategory, String endCategory) {
        return currentCategory != null
                && endCategory != null
                && endCategory.equals(currentCategory);
    }
}
