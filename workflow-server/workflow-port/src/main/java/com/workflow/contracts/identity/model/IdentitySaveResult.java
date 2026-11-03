package com.workflow.contracts.identity.model;

import lombok.Builder;

/** 写入结果；canonicalUsername 是稳定的流程身份，只有用户结果提供。 */
@Builder
public record IdentitySaveResult(IdentityObjectType objectType, String sourceCode, String externalId, String localId,
        String canonicalUsername, IdentitySaveOutcome outcome, Long appliedSourceVersion) {
}
