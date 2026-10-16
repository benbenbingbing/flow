package com.workflow.contracts.process.query.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 在同一预览输入和作用域下分页列出目标节点候选。
 * @param approval 对应审批输入
 * @param scopeKey 预览作用域
 * @param targetNodeId 目标节点
 * @param keyword 候选查询关键词
 * @param page 分页
 */
public record NextApproverOptionsQuery(
        NextApprovalQuery approval,
        String scopeKey,
        String targetNodeId,
        String keyword,
        ProcessPageRequest page) {
    public NextApproverOptionsQuery {
        Objects.requireNonNull(approval, "approval");
        targetNodeId = ProcessValues.required(targetNodeId, "targetNodeId");
        page = page == null ? ProcessPageRequest.defaults() : page;

    }

}
