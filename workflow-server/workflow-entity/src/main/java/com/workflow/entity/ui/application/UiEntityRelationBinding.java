package com.workflow.entity.ui.application;

import java.util.Map;

/** 页面只保存关系编码及使用方向，不能重新定义匹配字段或关系所有者。 */
final class UiEntityRelationBinding {
    /**
     * 初始化界面实体关系绑定，保存构造参数供后续方法使用。
     */
    private UiEntityRelationBinding() { }

    /**
     * 判断{@code reverse}条件是否成立，供调用方选择后续分支。
     *
     * @param relation 关系，作为 {@code equals} 的输入影响后续处理
     * @return {@code reverse}条件成立时为 true，否则为 false
     */
    static boolean reverse(Map<String, Object> relation) {
        return "ENTITY_RELATION".equals(relation.get("type"))
                && "REVERSE".equals(relation.get("direction"));
    }

    /**
     * 反向使用由目标实体定义的关系；不信任浏览器另传的 ownerEntityId。
     *
     * @param relation 关系，作为 {@code reverse} 的输入影响后续处理
     * @param sourceId 来源ID，后续用于处理归属方ID时定位或关联目标
     * @param targetId 目标ID，后续用于处理归属方ID时定位或关联目标
     * @return 处理后的归属方ID文本，供调用方比较或展示
     */
    static String ownerId(Map<String, Object> relation, String sourceId, String targetId) {
        return reverse(relation) ? targetId : sourceId;
    }
}
