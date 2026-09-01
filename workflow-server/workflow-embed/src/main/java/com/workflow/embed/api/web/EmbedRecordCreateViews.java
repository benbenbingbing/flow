package com.workflow.embed.api.web;

import java.util.List;

/** RECORD_CREATE 的最小稳定响应；不包含字段、组件或记录值投影。 */
public final class EmbedRecordCreateViews {

    private EmbedRecordCreateViews() {
    }

    public record CreateResult(
            String receiptId,
            CreatedRecord record,
            List<Object> effects,
            String clientMutationId) {
    }

    public record CreatedRecord(
            String id,
            Long recordVersion) {
    }
}
