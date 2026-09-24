package com.workflow.entity.form.application;

import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import java.util.Map;
import java.util.Set;

/** 编辑草稿和校验发布快照共用的表单结构约束，不读取草稿或发布数据。 */
public final class FormNodeStructurePolicy {
    private FormNodeStructurePolicy() {}

    public static final int MAX_DEPTH = 8;

    public static final Set<String> NODE_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "TAB", "COLLAPSE",
            "TEXT", "FIELD", "SUB_FORM", "REPEATER", "ACTION_SLOT");
    public static final Set<String> CONTAINER_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "TAB", "COLLAPSE", "SUB_FORM", "REPEATER");
    private static final Set<String> STANDARD_CONTAINER_CHILD_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "COLLAPSE",
            "TEXT", "FIELD", "SUB_FORM", "REPEATER", "ACTION_SLOT");
    private static final Map<String, Set<String>> ALLOWED_CHILD_TYPES = Map.of(
            "SECTION", STANDARD_CONTAINER_CHILD_TYPES,
            "GRID", STANDARD_CONTAINER_CHILD_TYPES,
            "TAB_SET", Set.of("TAB"),
            "TAB", STANDARD_CONTAINER_CHILD_TYPES,
            "COLLAPSE", STANDARD_CONTAINER_CHILD_TYPES,
            "SUB_FORM", STANDARD_CONTAINER_CHILD_TYPES,
            "REPEATER", STANDARD_CONTAINER_CHILD_TYPES);

    /**
     * 校验草稿或发布快照中一条父子关系：TAB 只能挂在 TAB_SET 下，叶子节点不能包含子节点。
     * parent 为 null 表示根节点；非法结构抛出 IllegalArgumentException，阻止保存或发布。
     */
    public static void validateParentChild(
            EntityFormNode child,
            EntityFormNode parent) {
        if (parent == null) {
            if ("TAB".equals(child.getNodeType())) {
                throw new IllegalArgumentException("TAB 节点只能位于 TAB_SET 下");
            }
            return;
        }
        Set<String> allowedChildren = ALLOWED_CHILD_TYPES.get(parent.getNodeType());
        if (allowedChildren == null || !allowedChildren.contains(child.getNodeType())) {
            if ("TAB".equals(child.getNodeType())) {
                throw new IllegalArgumentException("TAB 节点只能位于 TAB_SET 下");
            }
            if ("TAB_SET".equals(parent.getNodeType())) {
                throw new IllegalArgumentException("TAB_SET 的直接子节点只能是 TAB");
            }
            throw new IllegalArgumentException(
                    parent.getNodeType()
                            + " 节点不能直接包含 "
                            + child.getNodeType()
                            + " 节点");
        }
    }
}
