package com.workflow.embed.api.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Stable external projections returned by the Embed Runtime read APIs. */
public final class EmbedRuntimeViews {

    private EmbedRuntimeViews() {
    }

    public record Bootstrap(
            Session session,
            Actor actor,
            View view,
            List<String> capabilities,
            Ui ui,
            Limits limits,
            @JsonInclude(JsonInclude.Include.NON_NULL) Target target) {

        /** 兼容 LIST Runtime 调用方；LIST 没有原生 FORM target。 */
        public Bootstrap(
                Session session,
                Actor actor,
                View view,
                List<String> capabilities,
                Ui ui,
                Limits limits) {
            this(session, actor, view, capabilities, ui, limits, null);
        }
    }

    /**
     * 供 iframe 原生 Flow 页面启动的只读固定目标。
     *
     * <p>所有值均由 Session + immutable Release 恢复；浏览器只能消费，后续 API
     * 仍会在服务端重新比对。解析令牌仅授权同一发布快照及其声明的嵌套表单。</p>
     */
    public record Target(
            String entityCode,
            @JsonInclude(JsonInclude.Include.NON_NULL) String formId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String formReleaseId,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer formReleaseVersion,
            @JsonInclude(JsonInclude.Include.NON_NULL) String listKey,
            @JsonInclude(JsonInclude.Include.NON_NULL) String listReleaseId,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer listReleaseVersion,
            String entryMode,
            @JsonInclude(JsonInclude.Include.NON_NULL) String recordId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String processInstanceId,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String formReleaseResolutionToken,
            boolean defaultFormResolved,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String listReleaseResolutionToken,
            Map<String, Object> initialData,
            Map<String, Object> parameters,
            Map<String, Object> context) {
    }

    public record Session(String id, Instant expiresAt, Instant idleExpiresAt) {
    }

    /** 只用于 iframe 内存中的原生 UI 显隐，不是授权凭据。 */
    public record Actor(
            String username,
            String nickname,
            String displayName,
            List<String> roles,
            boolean isSuperAdmin,
            List<String> permissions) {

        /** 兼容 LIST 端的旧构造方式和单元测试。 */
        public Actor(String displayName) {
            this(displayName, displayName, displayName,
                    List.of(), false, List.of());
        }
    }

    public record View(
            String key,
            String name,
            String surfaceType,
            String entryMode) {
    }

    public record Ui(
            String locale,
            String theme,
            boolean showSearch,
            boolean showPagination,
            boolean showToolbar,
            int pageSize,
            String heightMode) {
    }

    public record Limits(
            int maxPageSize,
            int maxPayloadBytes,
            int maxSelectionSize) {
    }

    public record Schema(
            SchemaView view,
            Entity entity,
            ListSchema list,
            Object form,
            List<ExternalActionDescriptor> actions) {
    }

    public record SchemaView(String key, String surfaceType) {
    }

    public record Entity(String code, String name) {
    }

    public record ListSchema(
            Selection selection,
            Pagination pagination,
            List<Column> columns,
            List<Filter> filters) {
    }

    /**
     * 描述列表选择行为以及允许回传给宿主系统的值字段。
     *
     * <p>{@code returnableFields} 与展示列分离，iframe 可以渲染更多字段，但
     * {@code selection.changed} 只能回传发布快照明确授权的字段。</p>
     */
    public record Selection(
            String mode,
            String valueField,
            List<String> returnableFields) {
    }

    public record Pagination(boolean allowTotal, int maxPageSize) {
    }

    public record Column(
            String code,
            String label,
            String type,
            Integer width,
            boolean sortable,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<Option> options) {
    }

    public record Filter(
            String code,
            String label,
            String type,
            String operator,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<Option> options) {
    }

    public record Option(String label, Object value) {
    }

    public record ExternalActionDescriptor(
            String key,
            String label,
            String placement,
            String kind,
            String transport,
            String recordMode,
            String selectionMode,
            Object dataSchema,
            boolean requiresRecordVersion,
            Object inputSchema,
            boolean idempotencyRequired) {
    }

    public record ListResult(
            List<ListItem> items,
            boolean hasMore,
            int pageNum,
            int pageSize,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long total) {
    }

    public record ListItem(
            String id,
            Long recordVersion,
            Map<String, Object> values,
            ItemMeta meta,
            Map<String, ItemActionCapability> actions) {
    }

    public record ItemMeta(Instant updatedAt) {
    }

    public record ItemActionCapability(
            boolean visible,
            boolean enabled,
            String reason) {
    }
}
