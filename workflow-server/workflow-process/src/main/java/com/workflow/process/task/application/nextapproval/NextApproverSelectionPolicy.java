package com.workflow.process.task.application.nextapproval;

import java.util.List;
import java.util.Map;

/**
 * 已从发布 BPMN 中规范化的下一节点审批人选择策略。
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

    public enum SourceType {
        SCOPE,
        RESOLVER
    }

    public enum ScopeType {
        ALL_USERS,
        USER,
        ROLE,
        GROUP,
        ORGANIZATION
    }

    public record Scope(
            ScopeType type,
            List<String> values,
            boolean includeChildren) {
    }

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
