package com.workflow.contracts.embed;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Embed Runtime 读取已发布表单、记录与字段候选项的防腐层端口。
 *
 * <p>所有 {@link Target} 均由 Embed Session 固定的不可变 Release 恢复，不得从
 * 浏览器请求接收。适配器必须继续执行当前 Flow 用户的对象权限、数据范围和
 * 已发布表单规则，并且只能执行已审核的只读候选项来源。</p>
 */
public interface EmbedRuntimeFormPort {

    /** 解析精确固定的已发布表单及当前模式下的字段/动作状态。 */
    FormSnapshot resolveForm(ResolveQuery query);

    /**
     * 在当前 Flow 用户对象权限和数据范围内读取记录。
     *
     * <p>记录不存在与无权访问均必须返回 {@link Optional#empty()}，避免调用方
     * 通过差异错误枚举记录 ID。</p>
     */
    Optional<RecordSnapshot> findRecord(RecordQuery query);

    /** 查询已发布字段声明的只读动态选项。 */
    OptionPage queryOptions(OptionQuery query);

    /** 查询已发布引用字段的可访问候选记录。 */
    LookupPage queryLookups(LookupQuery query);

    enum RuntimeMode {
        CREATE,
        VIEW
    }

    enum PolicySource {
        CLIENT_WRITABLE,
        CURRENT_RECORD_READONLY,
        CONTEXT,
        CONSTANT
    }

    /** 由服务端恢复的不可变目标坐标。 */
    record Target(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion,
            String listKey,
            String listReleaseId,
            Integer listReleaseVersion) {
    }

    record ResolveQuery(
            Target target,
            RuntimeMode mode,
            String recordId,
            Map<String, Object> trustedRecordValues) {
    }

    record RecordQuery(
            Target target,
            String recordId,
            Map<String, Object> trustedContextFilters) {
    }

    record OptionQuery(
            Target target,
            RuntimeMode mode,
            String recordId,
            String fieldCode,
            String keyword,
            Map<String, Object> clientDependencies,
            Map<String, Object> trustedContext,
            int pageNum,
            int pageSize) {
    }

    record LookupQuery(
            Target target,
            RuntimeMode mode,
            String recordId,
            String fieldCode,
            String keyword,
            Map<String, Object> clientFilters,
            Map<String, Object> trustedContext,
            int pageNum,
            int pageSize) {
    }

    record FormSnapshot(
            String title,
            String layoutType,
            List<Field> fields,
            List<Action> actions) {
    }

    /**
     * 已发布字段的内部运行态投影。
     *
     * <p>该类型故意不包含 provider、service、operation、URL、script 或内部事件。
     * 选项/引用查询只暴露来源类别白名单，真实执行目标由适配器从同一 Release
     * 再次恢复并校验。</p>
     */
    record Field(
            String code,
            String label,
            String type,
            boolean required,
            boolean readOnly,
            boolean hidden,
            boolean modeVisible,
            boolean modeEditable,
            Object defaultValue,
            Map<String, Object> validation,
            List<Option> options,
            boolean runtimeOptions,
            List<DependencyRule> dependencyPolicy,
            boolean lookup,
            List<FilterRule> filterPolicy,
            List<String> lookupProjection,
            int span) {
    }

    record Option(String label, Object value, boolean disabled) {
    }

    record DependencyRule(
            String code,
            PolicySource source) {
    }

    record FilterRule(
            String code,
            PolicySource source,
            List<String> operators) {
    }

    record Action(
            String key,
            String label,
            boolean visible,
            boolean enabled,
            String reason) {
    }

    record RecordSnapshot(
            String id,
            Long recordVersion,
            Map<String, Object> values,
            Instant createdAt,
            Instant updatedAt) {
    }

    record OptionPage(
            List<Option> items,
            boolean hasMore,
            int pageNum,
            int pageSize) {
    }

    record LookupItem(
            String id,
            String label,
            Map<String, Object> values) {
    }

    record LookupPage(
            List<LookupItem> items,
            boolean hasMore,
            int pageNum,
            int pageSize) {
    }

    /** 候选项来源未经 Embed 白名单审核或当前字段不支持该查询。 */
    final class OperationNotAllowedException extends RuntimeException {
        public OperationNotAllowedException(String message) {
            super(message);
        }
    }
}
