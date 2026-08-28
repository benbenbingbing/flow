package com.workflow.embed.api.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Embed 表单与记录读取接口的稳定 External Projection。
 *
 * <p>这些 DTO 只表达 Shell 可渲染的封闭协议，故意不提供表单/Release 内部 ID、
 * 组件实现名、Provider、URL、脚本或内部事件字段。</p>
 */
public final class EmbedRuntimeFormViews {

    private EmbedRuntimeFormViews() {
    }

    public record FormResult(
            String mode,
            RecordView record,
            FormView form) {
    }

    public record FormView(
            String title,
            FormLayout layout,
            List<FieldView> fields,
            List<String> returnableFields,
            List<ActionDescriptor> actions) {
    }

    public record FormLayout(String type) {
    }

    public record FieldView(
            String code,
            String label,
            String type,
            boolean required,
            boolean readOnly,
            boolean hidden,
            Object defaultValue,
            Map<String, Object> validation,
            List<OptionView> options,
            OptionSource optionSource,
            LookupSource lookupSource,
            FieldLayout layout,
            FieldState fieldState) {
    }

    public record OptionView(
            String label,
            Object value,
            boolean disabled) {
    }

    public record OptionSource(
            String mode,
            String queryUrl,
            List<DependencyPolicy> dependencyPolicy) {
    }

    public record DependencyPolicy(
            String code,
            String source) {
    }

    public record LookupSource(
            String mode,
            String queryUrl,
            List<FilterPolicy> filterPolicy) {
    }

    public record FilterPolicy(
            String code,
            String source,
            List<String> operators) {
    }

    public record FieldLayout(int span) {
    }

    public record FieldState(
            boolean visible,
            boolean writable) {
    }

    public record ActionDescriptor(
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
            boolean idempotencyRequired,
            boolean enabled,
            String disabledReason) {
    }

    public record RecordView(
            String id,
            Long recordVersion,
            Map<String, Object> values,
            RecordMeta meta) {
    }

    public record RecordMeta(
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RecordResult(
            RecordView record,
            Map<String, RecordFieldState> fieldStates,
            Map<String, ActionCapability> actions) {
    }

    /** RECORD_CREATE 成功投影；effects V1 固定为空。 */
    public record CreateResult(
            String receiptId,
            RecordView record,
            List<Object> effects,
            String clientMutationId) {
    }

    public record RecordFieldState(
            boolean visible,
            boolean readOnly,
            boolean required) {
    }

    public record ActionCapability(
            boolean visible,
            boolean enabled,
            String reason) {
    }

    public record OptionPage(
            List<OptionView> items,
            boolean hasMore,
            int pageNum,
            int pageSize) {
    }

    public record LookupItem(
            String id,
            String label,
            Map<String, Object> values) {
    }

    public record LookupPage(
            List<LookupItem> items,
            boolean hasMore,
            int pageNum,
            int pageSize) {
    }
}
