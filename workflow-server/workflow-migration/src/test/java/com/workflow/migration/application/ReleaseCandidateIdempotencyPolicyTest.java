package com.workflow.migration.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseCandidateIdempotencyPolicyTest {

    private final ReleaseCandidateIdempotencyPolicy policy =
            new ReleaseCandidateIdempotencyPolicy();

    @Test
    void samePublishedRequestReturnsExistingResult() {
        assertEquals(
                ReleaseCandidateIdempotencyPolicy.Decision.RETURN_EXISTING,
                policy.decide("PUBLISHED", "request-1", "request-1", false));
    }

    @Test
    void differentKeyCannotRepublishPublishedCandidate() {
        assertEquals(
                ReleaseCandidateIdempotencyPolicy.Decision.REJECT,
                policy.decide("PUBLISHED", "request-1", "request-2", false));
    }

    @Test
    void onlyExplicitResumeCanExecuteFailedCandidate() {
        assertEquals(
                ReleaseCandidateIdempotencyPolicy.Decision.REJECT,
                policy.decide("FAILED", "request-1", "request-2", false));
        assertEquals(
                ReleaseCandidateIdempotencyPolicy.Decision.EXECUTE,
                policy.decide("FAILED", "request-1", "request-2", true));
    }
}
