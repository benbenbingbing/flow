package com.workflow.entity.form.uniqueness.application;

import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
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

    /**
     * 初始化表单唯一变更上下文，保存构造参数供后续方法使用。
     */
    private FormUniqueMutationContext() {
    }

    /**
     * 返回第一个可信表单发布身份，兼容单表单调用方。
     *
     * @param context 执行上下文，向后续表单唯一变更上下文步骤传递身份、配置或状态
     * @return 匹配的表单唯一变更上下文；未找到时为空
     */
    public static Optional<Reference> resolve(
            EntityMutationContext context) {
        return resolveAll(context).stream().findFirst();
    }

    /**
     * 解析本次变更实际应用的全部可信表单发布身份。
     *
     * <p>普通 FORM 使用单表单字段；审批任务使用服务端写入的 formReferences。
     * 其他来源即使携带同名参数也不会启用表单规则。</p>
     *
     * @param context 执行上下文，向后续表单唯一变更上下文全部步骤传递身份、配置或状态
     * @return 引用集合，供调用方遍历或展示
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
     *
     * @param references 引用，供本方法编码引用时使用
     * @return 引用键值结果，供调用方继续处理
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

    /**
     * 处理引用，并将结果传给后续步骤。
     *
     * @param params 参数，作为 {@code text} 的输入影响后续处理
     * @return 处理后的引用结果，供调用方继续处理
     */
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

    /**
     * 写入文本；后续读取或执行将使用更新后的状态。
     *
     * @param target 目标，供本方法写入文本时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param value 待写入文本的原始输入，结果供调用方继续使用
     */
    private static void putText(
            Map<String, Object> target,
            String key,
            String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(Object value) {
        return value == null
                ? null : String.valueOf(value).trim();
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 封装引用的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     * @param effectiveReleaseId 有效发布版本ID，后续用于处理引用时定位或关联目标
     * @param effectiveContentHash 有效内容哈希，保存在对象中供后续校验、查询或展示
     * @param hotfixTargetId 热修复目标ID，后续用于处理引用时定位或关联目标
     */
    public record Reference(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String effectiveReleaseId,
            String effectiveContentHash,
            String hotfixTargetId) {

        /**
         * 初始化引用，保存构造参数供后续方法使用。
         *
         * @param formId 表单 ID，后续用于定位已发布表单
         * @param releaseId 发布版本 ID，后续用于解析固定配置
         * @param releaseVersion 发布版本号，后续用于校验快照一致性
         * @param effectiveReleaseId 有效发布版本ID，后续用于初始化引用时定位或关联目标
         */
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

        /**
         * 初始化引用，保存构造参数供后续方法使用。
         *
         * @param formId 表单 ID，后续用于定位已发布表单
         * @param releaseId 发布版本 ID，后续用于解析固定配置
         * @param releaseVersion 发布版本号，后续用于校验快照一致性
         */
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

    /**
     * 封装引用键的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     * @param effectiveReleaseId 有效发布版本ID，后续用于处理引用键时定位或关联目标
     * @param effectiveContentHash 有效内容哈希，保存在对象中供后续校验、查询或展示
     * @param hotfixTargetId 热修复目标ID，后续用于处理引用键时定位或关联目标
     */
    private record ReferenceKey(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String effectiveReleaseId,
            String effectiveContentHash,
            String hotfixTargetId) {
    }
}
