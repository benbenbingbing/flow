package com.workflow.contracts.identity;

/**
 * 当前操作人的稳定跨模块表示。
 */
public record CurrentActor(String userId, String username) {
}
