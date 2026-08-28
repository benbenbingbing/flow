package com.workflow.entity.form.uniqueness.application;

import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.PreparedUniqueClaims;

/**
 * 在一次服务端调用链内，把子表单的可信发布身份传到关系写入层。
 *
 * <p>载体是私有 Java 类型，不是 Map 或字符串；因此客户端即使伪造同名
 * JSON 字段也无法生成可被信任的标记。关系写入层会在生成 SQL 前一次性
 * 移除该值。@JsonValue 只让变更幂等内部摘要计算能处理该对象，不序列化
 * 发布身份本身。</p>
 */
public final class TrustedSubFormUniqueReference {

    private static final String TRANSPORT_KEY =
            "$__trusted_subform_unique_release";

    private TrustedSubFormUniqueReference() {
    }

    /**
     * 覆盖客户端同名值并附加子实体编码及服务端刚解析的发布引用。
     *
     * <p>同一子行可能依次经过多个父表单的 SUB_FORM 节点。只有已有值也是
     * 本进程创建的私有 Marker 时才合并，并按完整发布身份稳定去重；客户端
     * 同名 Map/字符串仍会被直接覆盖，不能借此激活额外规则。</p>
     */
    public static void attach(
            Map<String, Object> row,
            String entityCode,
            FormUniqueMutationContext.Reference reference) {
        if (row == null) {
            throw new IllegalArgumentException(
                    "子表单行不能为空");
        }
        if (!StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException(
                    "子表单可信发布引用必须提供实体编码");
        }
        if (reference == null
                || !StringUtils.hasText(reference.formId())) {
            throw new IllegalArgumentException(
                    "子表单可信发布引用不完整");
        }
        Object existing = row.get(TRANSPORT_KEY);
        List<FormUniqueMutationContext.Reference> references =
                new ArrayList<>();
        if (existing instanceof Marker marker) {
            references.addAll(marker.references());
            if (!marker.entityCode().equals(entityCode)) {
                throw new IllegalArgumentException(
                        "同一子表单行不能绑定不同实体");
            }
        }
        references.add(reference);
        row.put(TRANSPORT_KEY, new Marker(
                entityCode,
                List.copyOf(new LinkedHashSet<>(references)),
                existing instanceof Marker marker
                        ? marker.prepared() : null));
    }

    /**
     * 写库前移除内部字段，并仅在值是本进程构造的私有类型时返回引用。
     */
    public static List<FormUniqueMutationContext.Reference>
            remove(Map<String, Object> row) {
        return removePrepared(row).references();
    }

    /** 写库前原子移除可信引用及其写前 gate 凭据。 */
    public static Resolved removePrepared(
            Map<String, Object> row) {
        if (row == null) {
            return Resolved.empty();
        }
        Object value = row.remove(TRANSPORT_KEY);
        return value instanceof Marker marker
                ? new Resolved(
                        marker.entityCode(),
                        marker.references(),
                        marker.prepared())
                : Resolved.empty();
    }

    /** 在可能修改 payload 的阶段前，捕获可信 Marker 的路径与对象身份。 */
    public static PayloadSnapshot snapshot(Object root) {
        return new PayloadSnapshot(pending(root).stream()
                .sorted(java.util.Comparator.comparing(Pending::path))
                .map(value -> new MarkerSnapshot(
                        value.path(),
                        value.entityCode(),
                        value.references(),
                        value.markerIdentity()))
                .toList());
    }

    /**
     * 确认 payload 变换没有剥离、替换或移动可信 Marker。
     * 普通字段值可以改变；唯一候选会在全部变换完成后重新准备。
     */
    public static void requireUnchanged(
            PayloadSnapshot expected,
            Object actualRoot) {
        PayloadSnapshot checked = expected == null
                ? new PayloadSnapshot(List.of()) : expected;
        List<Pending> actual = pending(actualRoot).stream()
                .sorted(java.util.Comparator.comparing(Pending::path))
                .toList();
        if (checked.markers.size() != actual.size()) {
            throw new IllegalStateException(
                    "实体变换剥离或新增了可信子表单标记");
        }
        for (int index = 0; index < actual.size(); index++) {
            MarkerSnapshot before = checked.markers.get(index);
            Pending after = actual.get(index);
            if (!Objects.equals(before.path(), after.path())
                    || !Objects.equals(
                            before.entityCode(), after.entityCode())
                    || !Objects.equals(
                            before.references(), after.references())
                    || before.markerIdentity()
                    != after.markerIdentity()) {
                throw new IllegalStateException(
                        "实体变换替换或移动了可信子表单标记");
            }
        }
    }

    /** 仅由唯一性准备服务递归读取并绑定不可伪造的 prepared token。 */
    static List<Pending> pending(Object root) {
        List<Pending> result = new ArrayList<>();
        collect(root, "", result);
        return List.copyOf(result);
    }

    private static void collect(
            Object value,
            String path,
            List<Pending> result) {
        if (value instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) raw;
            Object marker = row.get(TRANSPORT_KEY);
            if (marker instanceof Marker trusted
                    && StringUtils.hasText(trusted.entityCode())) {
                result.add(new Pending(
                        path,
                        row,
                        trusted.entityCode(),
                        trusted.references(),
                        trusted.prepared(),
                        trusted));
            }
            row.forEach((key, item) -> {
                if (!TRANSPORT_KEY.equals(String.valueOf(key))) {
                    collect(
                            item,
                            path + "/" + escape(
                                    String.valueOf(key)),
                            result);
                }
            });
        } else if (value instanceof Iterable<?> values) {
            int index = 0;
            for (Object item : values) {
                collect(item, path + "/" + index, result);
                index++;
            }
        }
    }

    private static String escape(String value) {
        return value.replace("~", "~0")
                .replace("/", "~1");
    }

    static void bind(
            Pending pending,
            PreparedUniqueClaims prepared) {
        Object value = pending.row().get(TRANSPORT_KEY);
        if (!(value instanceof Marker marker)
                || marker != pending.markerIdentity()
                || !pending.references().equals(marker.references())
                || !pending.entityCode().equals(marker.entityCode())) {
            throw new IllegalStateException(
                    "子表单可信发布标记在 gate 准备期间发生变化");
        }
        pending.row().put(
                TRANSPORT_KEY,
                new Marker(
                        marker.entityCode(),
                        marker.references(),
                        prepared));
    }

    /** 仅供同包测试验证普通 JSON 值不能伪造私有标记。 */
    static String transportKey() {
        return TRANSPORT_KEY;
    }

    public record Resolved(
            String entityCode,
            List<FormUniqueMutationContext.Reference> references,
            PreparedUniqueClaims prepared) {

        private static Resolved empty() {
            return new Resolved(null, List.of(), null);
        }
    }

    record Pending(
            String path,
            Map<String, Object> row,
            String entityCode,
            List<FormUniqueMutationContext.Reference> references,
            PreparedUniqueClaims prepared,
            Object markerIdentity) {
    }

    /** 对调用方不暴露 Marker 内容的不可变 payload 快照。 */
    public static final class PayloadSnapshot {

        private final List<MarkerSnapshot> markers;

        private PayloadSnapshot(List<MarkerSnapshot> markers) {
            this.markers = List.copyOf(markers);
        }
    }

    private record MarkerSnapshot(
            String path,
            String entityCode,
            List<FormUniqueMutationContext.Reference> references,
            Object markerIdentity) {
    }

    private record Marker(
            String entityCode,
            List<FormUniqueMutationContext.Reference> references,
            PreparedUniqueClaims prepared) {

        private Marker {
            references = List.copyOf(references);
        }

        /** 内部命令摘要不应包含发布身份对象的序列化结构。 */
        @JsonValue
        public String serializedPlaceholder() {
            return "SERVER_TRUSTED_SUBFORM_RELEASE";
        }
    }
}
