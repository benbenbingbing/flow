package com.workflow.entity.data.domain.policy;

/**
 * 流程结束时的实体状态保留规则。
 */
public final class EntityProcessStatusPolicy {

    /**
     * 初始化实体流程状态策略，保存构造参数供后续方法使用。
     */
    private EntityProcessStatusPolicy() {
    }

    /**
     * 判断是否需要{@code preserve}；判断结果决定调用方的后续分支。
     *
     * @param currentCategory 当前类别，决定后续状态或结果的归类
     * @param endCategory 结束类别，决定后续状态或结果的归类
     * @return {@code preserve}条件成立时为 true，否则为 false
     */
    public static boolean shouldPreserve(String currentCategory, String endCategory) {
        return currentCategory != null
                && endCategory != null
                && endCategory.equals(currentCategory);
    }
}
