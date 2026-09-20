package com.workflow.entity.ui.application;

import java.util.Map;

/** 页面只保存关系编码及使用方向，不能重新定义匹配字段或关系所有者。 */
final class UiEntityRelationBinding {
    private UiEntityRelationBinding() { }

    static boolean reverse(Map<String, Object> relation) {
        return "ENTITY_RELATION".equals(relation.get("type"))
                && "REVERSE".equals(relation.get("direction"));
    }

    /** 反向使用由目标实体定义的关系；不信任浏览器另传的 ownerEntityId。 */
    static String ownerId(Map<String, Object> relation, String sourceId, String targetId) {
        return reverse(relation) ? targetId : sourceId;
    }
}
