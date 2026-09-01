package com.workflow.contracts.embed;

import java.util.List;

/**
 * 一次 Embed Launch 固定的原生列表依赖闭包。
 *
 * <p>闭包从根 List Release 的 open-list 边递归生成，并随 Runtime Snapshot 和
 * {@code elr1} 令牌一起固定。运行时只能在闭包声明的精确坐标间导航，不能重新解析
 * ACTIVE，也不能把浏览器提交的坐标当作授权依据。</p>
 */
public record EmbedNativeListDependencyClosure(
        int version,
        List<ListNode> nodes) {

    public static final int CURRENT_VERSION = 1;

    public EmbedNativeListDependencyClosure {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    /** 闭包中的一个精确列表版本、其新增表单结论以及允许导航的目标边。 */
    public record ListNode(
            ListCoordinate list,
            boolean defaultFormResolved,
            FormCoordinate defaultForm,
            List<ListCoordinate> targets) {

        public ListNode {
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }

    /** 列表稳定标识和精确发布坐标；listConfigId 用于阻止同 key 资源替换。 */
    public record ListCoordinate(
            String entityCode,
            String listKey,
            String listConfigId,
            String listReleaseId,
            int listReleaseVersion) {
    }

    /** Flow 原生 new-data 规则在物化时解析出的可选默认表单精确坐标。 */
    public record FormCoordinate(
            String formId,
            String formReleaseId,
            int formReleaseVersion) {
    }
}
