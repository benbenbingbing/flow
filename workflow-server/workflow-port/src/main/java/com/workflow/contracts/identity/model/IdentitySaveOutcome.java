package com.workflow.contracts.identity.model;

/** 保存结果，明确区分幂等返回与实际变更。 */
public enum IdentitySaveOutcome { CREATED, UPDATED, UNCHANGED, STALE_IGNORED }
