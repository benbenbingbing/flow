package com.workflow.contracts.identity.model;

import lombok.Builder;

/** 来源记录维护信息；本地停用独立于来源状态，页面据此展示只读基础资料。 */
@Builder
public record IdentitySourceInfo(String sourceCode, String externalAccount, boolean sourceEnabled, boolean localAllowed) {
}
