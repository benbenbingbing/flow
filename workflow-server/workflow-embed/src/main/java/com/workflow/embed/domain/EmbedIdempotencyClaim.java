package com.workflow.embed.domain;

/** 当前 Embed 写请求对共享幂等记录的认领结果。 */
public record EmbedIdempotencyClaim(
        String id,
        long fencingToken,
        Disposition disposition,
        Integer responseStatus,
        String responseBody,
        String resourceType,
        String resourceId) {

    public boolean acquired() {
        return disposition == Disposition.ACQUIRED;
    }

    public boolean replay() {
        return disposition == Disposition.REPLAY;
    }

    public boolean processing() {
        return disposition == Disposition.PROCESSING;
    }

    public enum Disposition {
        ACQUIRED,
        REPLAY,
        PROCESSING
    }
}
