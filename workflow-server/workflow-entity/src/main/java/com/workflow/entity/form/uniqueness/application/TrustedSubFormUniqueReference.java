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

    /**
     * 初始化可信子级表单唯一引用，保存构造参数供后续方法使用。
     */
    private TrustedSubFormUniqueReference() {
    }

    /**
     * 覆盖客户端同名值并附加子实体编码及服务端刚解析的发布引用。
     *
     * <p>同一子行可能依次经过多个父表单的 SUB_FORM 节点。只有已有值也是
     * 本进程创建的私有 Marker 时才合并，并按完整发布身份稳定去重；客户端
     * 同名 Map/字符串仍会被直接覆盖，不能借此激活额外规则。</p>
     *
     * @param row 行，供本方法处理{@code attach}时使用
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param reference 引用，作为 {@code references.add} 的输入影响后续处理
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
     *
     * @param row 行，作为 {@code removePrepared} 的输入影响后续处理
     * @return 表单唯一变更上下文集合，供调用方遍历或展示
     */
    public static List<FormUniqueMutationContext.Reference>
            remove(Map<String, Object> row) {
        return removePrepared(row).references();
    }

    /**
     * 写库前原子移除可信引用及其写前 gate 凭据。
     *
     * @param row 行，供本方法移除已准备时使用
     * @return 移除后的已准备结果，供调用方继续处理
     */
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

    /**
     * 在可能修改 payload 的阶段前，捕获可信 Marker 的路径与对象身份。
     *
     * @param root 根，作为 {@code PayloadSnapshot} 的输入影响后续处理
     * @return 处理后的快照结果，供调用方继续处理
     */
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
     *
     * @param expected 预期，供本方法校验并获取{@code unchanged}时使用
     * @param actualRoot 实际根，作为 {@code pending} 的输入影响后续处理
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

    /**
     * 仅由唯一性准备服务递归读取并绑定不可伪造的 prepared token。
     *
     * @param root 根，作为 {@code collect} 的输入影响后续处理
     * @return 待处理集合，供调用方遍历或展示
     */
    static List<Pending> pending(Object root) {
        List<Pending> result = new ArrayList<>();
        collect(root, "", result);
        return List.copyOf(result);
    }

    /**
     * 收集可信子级表单唯一引用；结果供调用方的后续步骤使用。
     *
     * @param value 待收集可信子级表单唯一引用的原始输入，结果供调用方继续使用
     * @param path 路径，作为 {@code result.add} 的输入影响后续处理
     * @param result 结果，供本方法收集可信子级表单唯一引用时使用
     */
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

    /**
     * 生成{@code escape}文本，供后续匹配或展示。
     *
     * @param value 待处理{@code escape}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code escape}文本，供调用方比较或展示
     */
    private static String escape(String value) {
        return value.replace("~", "~0")
                .replace("/", "~1");
    }

    /**
     * 处理绑定，并将结果传给后续步骤。
     *
     * @param pending 待处理，供本方法处理绑定时使用
     * @param prepared 已准备，供本方法处理绑定时使用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 仅供同包测试验证普通 JSON 值不能伪造私有标记。
     *
     * @return 处理后的传输键文本，供调用方比较或展示
     */
    static String transportKey() {
        return TRANSPORT_KEY;
    }

    /**
     * 封装已解析的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param references 引用，保存在对象中供后续校验、查询或展示
     * @param prepared 已准备，保存在对象中供后续校验、查询或展示
     */
    public record Resolved(
            String entityCode,
            List<FormUniqueMutationContext.Reference> references,
            PreparedUniqueClaims prepared) {

        /**
         * 处理空，并将结果传给后续步骤。
         *
         * @return 处理后的空结果，供调用方继续处理
         */
        private static Resolved empty() {
            return new Resolved(null, List.of(), null);
        }
    }

    /**
     * 封装待处理的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param path 路径，保存在对象中供后续校验、查询或展示
     * @param row 行，保存在对象中供后续校验、查询或展示
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param references 引用，保存在对象中供后续校验、查询或展示
     * @param prepared 已准备，保存在对象中供后续校验、查询或展示
     * @param markerIdentity {@code marker}身份，保存在对象中供后续校验、查询或展示
     */
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

        /**
         * 初始化载荷快照，保存构造参数供后续方法使用。
         *
         * @param markers {@code markers}，保存在对象中供后续校验、查询或展示
         */
        private PayloadSnapshot(List<MarkerSnapshot> markers) {
            this.markers = List.copyOf(markers);
        }
    }

    /**
     * 封装{@code marker}快照的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param path 路径，保存在对象中供后续校验、查询或展示
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param references 引用，保存在对象中供后续校验、查询或展示
     * @param markerIdentity {@code marker}身份，保存在对象中供后续校验、查询或展示
     */
    private record MarkerSnapshot(
            String path,
            String entityCode,
            List<FormUniqueMutationContext.Reference> references,
            Object markerIdentity) {
    }

    /**
     * 封装{@code marker}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param references 引用，保存在对象中供后续校验、查询或展示
     * @param prepared 已准备，保存在对象中供后续校验、查询或展示
     */
    private record Marker(
            String entityCode,
            List<FormUniqueMutationContext.Reference> references,
            PreparedUniqueClaims prepared) {

        /**
         * 初始化{@code marker}，保存构造参数供后续方法使用。
         *
         * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
         * @param references 引用，保存在对象中供后续校验、查询或展示
         * @param prepared 已准备，保存在对象中供后续校验、查询或展示
         */
        private Marker {
            references = List.copyOf(references);
        }

        /**
         * 内部命令摘要不应包含发布身份对象的序列化结构。
         *
         * @return 处理后的{@code serialized}{@code placeholder}文本，供调用方比较或展示
         */
        @JsonValue
        public String serializedPlaceholder() {
            return "SERVER_TRUSTED_SUBFORM_RELEASE";
        }
    }
}
