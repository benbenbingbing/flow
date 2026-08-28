package com.workflow.entity.form.uniqueness.application;

import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 统一实体变更上下文中的可信表单发布身份。
 *
 * <p>这些值由服务端完成表单发布授权后写入，事务内唯一规则解析不得接受客户端
 * 直接提交的规则文档。</p>
 */
public final class FormUniqueMutationContext {

    public static final String FORM_ID = "formId";
    public static final String FORM_RELEASE_ID = "formReleaseId";
    public static final String FORM_RELEASE_VERSION =
            "formReleaseVersion";
    public static final String FORM_EFFECTIVE_RELEASE_ID =
            "formEffectiveReleaseId";
    public static final String FORM_EFFECTIVE_CONTENT_HASH =
            "formEffectiveContentHash";
    public static final String FORM_HOTFIX_TARGET_ID =
            "formHotfixTargetId";
    public static final String FORM_REFERENCES =
            "formReferences";

    private FormUniqueMutationContext() {
    }

    /** 返回第一个可信表单发布身份，兼容单表单调用方。 */
    public static Optional<Reference> resolve(
            EntityMutationContext context) {
        return resolveAll(context).stream().findFirst();
    }

    /**
     * 解析本次变更实际应用的全部可信表单发布身份。
     *
     * <p>普通 FORM 使用单表单字段；审批任务使用服务端写入的 formReferences。
     * 其他来源即使携带同名参数也不会启用表单规则。</p>
     */
    public static List<Reference> resolveAll(
            EntityMutationContext context) {
        if (context == null) {
            return List.of();
        }
        Map<String, Object> params = context.extraParams();
        if (context.sourceType() == EntityMutationSourceType.FORM) {
            Reference reference = reference(params);
            return reference == null
                    ? List.of() : List.of(reference);
        }
        if (context.sourceType()
                != EntityMutationSourceType.APPROVAL_TASK) {
            return List.of();
        }
        Object configured = params.get(FORM_REFERENCES);
        if (!(configured instanceof List<?> values)
                || values.isEmpty()) {
            return List.of();
        }
        Map<ReferenceKey, Reference> result =
                new LinkedHashMap<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException(
                        "审批表单发布身份必须是对象列表");
            }
            Map<String, Object> source = new LinkedHashMap<>();
            raw.forEach((key, item) -> source.put(
                    String.valueOf(key), item));
            Reference reference = reference(source);
            if (reference == null) {
                throw new IllegalArgumentException(
                        "审批表单发布身份缺少表单ID");
            }
            result.putIfAbsent(
                    new ReferenceKey(
                            reference.formId(),
                            reference.releaseId(),
                            reference.releaseVersion(),
                            reference.effectiveReleaseId(),
                            reference.effectiveContentHash(),
                            reference.hotfixTargetId()),
                    reference);
        }
        return List.copyOf(result.values());
    }

    /**
     * 将服务端已经应用的 1..N 个发布表单编码到审批变更上下文。
     * 返回内容只包含稳定发布身份，不包含短期 resolution token。
     */
    public static Map<String, Object> encodeReferences(
            List<Reference> references) {
        if (references == null || references.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> encoded = new ArrayList<>();
        for (Reference reference : references) {
            if (reference == null
                    || !StringUtils.hasText(reference.formId())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(FORM_ID, reference.formId());
            putText(item, FORM_RELEASE_ID, reference.releaseId());
            if (reference.releaseVersion() != null) {
                item.put(
                        FORM_RELEASE_VERSION,
                        reference.releaseVersion());
            }
            putText(
                    item,
                    FORM_EFFECTIVE_RELEASE_ID,
                    reference.effectiveReleaseId());
            putText(
                    item,
                    FORM_EFFECTIVE_CONTENT_HASH,
                    reference.effectiveContentHash());
            putText(
                    item,
                    FORM_HOTFIX_TARGET_ID,
                    reference.hotfixTargetId());
            encoded.add(Collections.unmodifiableMap(item));
        }
        return encoded.isEmpty()
                ? Map.of()
                : Map.of(
                        FORM_REFERENCES,
                        List.copyOf(encoded));
    }

    private static Reference reference(
            Map<String, Object> params) {
        String formId = text(params.get(FORM_ID));
        if (!StringUtils.hasText(formId)) {
            return null;
        }
        String releaseId = text(params.get(FORM_RELEASE_ID));
        String effectiveReleaseId = text(
                params.get(FORM_EFFECTIVE_RELEASE_ID));
        return new Reference(
                formId,
                releaseId,
                integer(params.get(FORM_RELEASE_VERSION)),
                StringUtils.hasText(effectiveReleaseId)
                        ? effectiveReleaseId : releaseId,
                text(params.get(FORM_EFFECTIVE_CONTENT_HASH)),
                text(params.get(FORM_HOTFIX_TARGET_ID)));
    }

    private static void putText(
            Map<String, Object> target,
            String key,
            String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private static String text(Object value) {
        return value == null
                ? null : String.valueOf(value).trim();
    }

    private static Integer integer(Object value) {
        if (value == null
                || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "表单发布版本必须是整数",
                    exception);
        }
    }

    public record Reference(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String effectiveReleaseId,
            String effectiveContentHash,
            String hotfixTargetId) {

        public Reference(
                String formId,
                String releaseId,
                Integer releaseVersion,
                String effectiveReleaseId) {
            this(
                    formId,
                    releaseId,
                    releaseVersion,
                    effectiveReleaseId,
                    null,
                    null);
        }

        public Reference(
                String formId,
                String releaseId,
                Integer releaseVersion) {
            this(
                    formId,
                    releaseId,
                    releaseVersion,
                    releaseId,
                    null,
                    null);
        }
    }

    private record ReferenceKey(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String effectiveReleaseId,
            String effectiveContentHash,
            String hotfixTargetId) {
    }
}
