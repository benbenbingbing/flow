package com.workflow.process.assignment.domain;

import java.util.List;

/** 结构化办理人解析结果，空结果和解析错误不会再用 null 或空集合静默表达。 */
public record AssigneeResolutionResult(
        Status status,
        List<String> usernames,
        String reasonCode,
        String reasonMessage,
        String resolverCode) {

    public static AssigneeResolutionResult resolved(
            List<String> usernames,
            String resolverCode) {
        return new AssigneeResolutionResult(
                Status.RESOLVED, List.copyOf(usernames), null, null, resolverCode);
    }

    public static AssigneeResolutionResult empty(
            String reasonCode,
            String reasonMessage,
            String resolverCode) {
        return new AssigneeResolutionResult(
                Status.EMPTY, List.of(), reasonCode, reasonMessage, resolverCode);
    }

    public static AssigneeResolutionResult error(
            String reasonCode,
            String reasonMessage,
            String resolverCode) {
        return new AssigneeResolutionResult(
                Status.ERROR, List.of(), reasonCode, reasonMessage, resolverCode);
    }

    public boolean resolved() {
        return status == Status.RESOLVED && !usernames.isEmpty();
    }

    public enum Status {
        RESOLVED,
        EMPTY,
        ERROR
    }
}
