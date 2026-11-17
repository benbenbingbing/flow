package com.workflow.contracts.identity.model;

import java.util.List;

/** 单个用户组的审批成员范围；空标签表示全组，多标签按或匹配，标签 ID 不随改名改变。 */
public record GroupMemberFilter(String groupCode, List<String> tagIds) {
    public GroupMemberFilter {
        if (groupCode == null || groupCode.isBlank() || groupCode.length() > 50) {
            throw new IllegalArgumentException("用户组编码不能为空且不能超过50字符");
        }
        groupCode = groupCode.trim();
        if (tagIds == null || tagIds.size() > 100
                || tagIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 64)) {
            throw new IllegalArgumentException("标签列表非法或超过100个");
        }
        tagIds = tagIds.stream().map(String::trim).distinct().toList();
    }
}
