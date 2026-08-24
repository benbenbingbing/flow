package com.workflow.migration.application;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 发布候选幂等状态判定，集中约束重复请求和并发发布。 */
@Component
public class ReleaseCandidateIdempotencyPolicy {

    public Decision decide(
            String status,
            String storedKey,
            String requestedKey,
            boolean resume) {
        if (!StringUtils.hasText(requestedKey)) {
            return Decision.REJECT;
        }
        boolean sameKey = requestedKey.equals(storedKey);
        if ("PUBLISHED".equals(status)) {
            return sameKey ? Decision.RETURN_EXISTING : Decision.REJECT;
        }
        if ("PUBLISHING".equals(status)) {
            return sameKey ? Decision.IN_PROGRESS : Decision.REJECT;
        }
        if (resume) {
            return "FAILED".equals(status) ? Decision.EXECUTE : Decision.REJECT;
        }
        return "READY".equals(status) ? Decision.EXECUTE : Decision.REJECT;
    }

    public enum Decision {
        EXECUTE,
        RETURN_EXISTING,
        IN_PROGRESS,
        REJECT
    }
}
