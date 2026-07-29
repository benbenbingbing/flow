package com.workflow.openapi.api.response;

import java.util.List;

public record OpenPage<T>(
        List<T> items,
        OpenPageMetadata page) {
}
