package com.workflow.contracts.identity.model;

import lombok.Builder;

/** 稳定来源键；sourceCode 必须经过部署授权，externalId 不得复用。 */
@Builder
public record ExternalIdentityKey(String sourceCode, String externalId) {
}
