package com.workflow.openapi.api.response;

public record OpenPageMetadata(
        String nextCursor,
        boolean hasMore) {
}
