package com.workflow.process.task.application.nextapproval;

import java.util.List;
import java.util.Map;

/**
 * 已从发布 BPMN 中规范化的下一节点审批人选择策略。
 *
 * @param configured 已配置，保存在对象中供后续校验、查询或展示
 * @param version 版本，保存在对象中供后续校验、查询或展示
 * @param visible 可见，保存在对象中供后续校验、查询或展示
 * @param editable 可编辑，保存在对象中供后续校验、查询或展示
 * @param assignmentMode 人员分配模式，后续选择解析器和校验规则
 * @param multiple {@code multiple}，保存在对象中供后续校验、查询或展示
 * @param sourceType 来源类型标识，决定后续下一步审批人选择策略采用的处理分支
 * @param scopes {@code scopes}，保存在对象中供后续校验、查询或展示
 * @param resolverCode 解析器编码，后续用于处理下一步审批人选择策略时定位或关联目标
 * @param extraParams 附加参数，后续传给解析器或执行器
 * @param scopeKey 作用域键，后续用于授权校验、关联或幂等去重
 */
public record NextApproverSelectionPolicy(
        boolean configured,
        int version,
        boolean visible,
        boolean editable,
        String assignmentMode,
        boolean multiple,
        SourceType sourceType,
        List<Scope> scopes,
        String resolverCode,
        Map<String, Object> extraParams,
        String scopeKey) {

    /**
     * 定义来源类型的可选值；调用方据此选择对应的处理分支。
     */
    public enum SourceType {
        SCOPE,
        RESOLVER,
        /**
         * 复用目标节点自身的办理人配置作为可选人员边界。
         *
         * <p>该来源不是新的人员池：固定人员、候选用户/组/角色以及人员解析器
         * 都按目标节点真实分配模式展开，确保预览、改选校验与任务创建同源。</p>
         */
        NODE_ASSIGNMENT
    }

    /**
     * 定义作用域类型的可选值；调用方据此选择对应的处理分支。
     */
    public enum ScopeType {
        ALL_USERS,
        USER,
        ROLE,
        GROUP,
        ORGANIZATION
    }

    /**
     * 封装作用域的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param type 类型标识，决定后续作用域采用的处理分支
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param includeChildren {@code include}子节点，保存在对象中供后续校验、查询或展示
     */
    public record Scope(
            ScopeType type,
            List<String> values,
            boolean includeChildren) {
    }

    /**
     * 处理{@code absent}，并将结果传给后续步骤。
     *
     * @return 处理后的{@code absent}结果，供调用方继续处理
     */
    public static NextApproverSelectionPolicy absent() {
        return new NextApproverSelectionPolicy(
                false,
                1,
                false,
                false,
                "DIRECT",
                false,
                null,
                List.of(),
                null,
                Map.of(),
                null);
    }
}
