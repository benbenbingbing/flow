package com.workflow.openapi.api.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.workflow.contracts.embed.launch.model.EmbedLaunchCommand;
import com.workflow.contracts.embed.launch.model.EmbedLaunchEntry;
import com.workflow.contracts.embed.launch.model.EmbedLaunchSubject;
import com.workflow.contracts.embed.launch.model.EmbedLaunchUi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * HTTP request for creating a one-time Embed launch.
 *
 * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
 * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
 * @param channelId 通道ID，后续用于处理打开嵌入式启动记录请求时定位或关联目标
 * @param subject 主体，保存在对象中供后续校验、查询或展示
 * @param entry 入口，保存在对象中供后续校验、查询或展示
 * @param context 执行上下文，向后续打开嵌入式启动记录请求步骤传递身份、配置或状态
 * @param ui 界面，保存在对象中供后续校验、查询或展示
 */
public record OpenEmbedLaunchRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9._-]{0,99}$")
        String viewKey,
        @NotBlank @Size(max = 255) String parentOrigin,
        @NotBlank
        @Size(min = 16, max = 128)
        @Pattern(regexp = "^[A-Za-z0-9._:-]+$")
        String channelId,
        @NotNull @Valid Subject subject,
        @NotNull @Valid Entry entry,
        @Size(max = 32) Map<String, Object> context,
        @Valid Ui ui) {

    /**
     * Launch 是安全授权边界，未知字段不能跟随 Spring Boot 的宽松 Jackson 默认值被静默忽略。
     *
     * @param name 名称，后续用于处理驳回{@code unknown}字段时匹配或展示
     * @param value 待处理驳回{@code unknown}字段的原始输入，结果供调用方继续使用
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Launch request contains unsupported fields");
    }

    /**
     * 人员凭据按类型使用互斥字段，避免把缺失断言推迟到业务层才发现。
     *
     * @return 主体{@code shape}有效条件成立时为 true，否则为 false
     */
    @AssertTrue(message = "subject fields do not match subject.type")
    public boolean isSubjectShapeValid() {
        if (subject == null || !hasText(subject.type())) {
            return false;
        }
        if ("SIGNED_JWT".equals(subject.type())) {
            return subject.assertionPresent() && hasText(subject.assertion())
                    && !subject.namespacePresent()
                    && !subject.externalUserIdPresent();
        }
        if ("TRUSTED_EXTERNAL_ID".equals(subject.type())) {
            return !subject.assertionPresent()
                    && subject.namespacePresent() && hasText(subject.namespace())
                    && subject.externalUserIdPresent() && hasText(subject.externalUserId());
        }
        return false;
    }

    /**
     * LIST/CREATE 不允许候选记录，VIEW 必须固定 recordId；V1 不接受编辑入口。
     *
     * @return 入口{@code shape}有效条件成立时为 true，否则为 false
     */
    @AssertTrue(message = "entry.recordId does not match entry.mode")
    public boolean isEntryShapeValid() {
        if (entry == null || !hasText(entry.mode())) {
            return false;
        }
        return switch (entry.mode()) {
            case "LIST", "CREATE" -> !entry.recordIdPresent();
            case "VIEW" -> entry.recordIdPresent() && hasText(entry.recordId());
            default -> false;
        };
    }

    /**
     * Converts web-bound data to the stable Launch use-case contract.
     *
     * @return 转换为后的命令结果，供调用方继续处理
     */
    public EmbedLaunchCommand toCommand() {
        return new EmbedLaunchCommand(
                viewKey,
                parentOrigin,
                channelId,
                new EmbedLaunchSubject(
                        subject.type(),
                        subject.assertion(),
                        subject.namespace(),
                        subject.externalUserId()),
                new EmbedLaunchEntry(entry.mode(), entry.recordId()),
                context,
                ui == null ? null : new EmbedLaunchUi(
                        ui.locale, ui.theme, ui.formPresentation));
    }

    /**
     * 判断是否具有文本；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否具有文本的原始输入，结果供调用方继续使用
     * @return 文本条件成立时为 true，否则为 false
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * External user assertion accepted by the V1 Launch endpoint.
     */
    public static final class Subject {

        @JsonProperty("type")
        @NotBlank
        @Pattern(regexp = "SIGNED_JWT|TRUSTED_EXTERNAL_ID")
        private String type;
        @JsonProperty("assertion")
        @Size(max = 16_384)
        private String assertion;
        @JsonProperty("namespace")
        @Size(max = 128)
        private String namespace;
        @JsonProperty("externalUserId")
        @Size(max = 128)
        private String externalUserId;

        @JsonIgnore
        private boolean assertionPresent;
        @JsonIgnore
        private boolean namespacePresent;
        @JsonIgnore
        private boolean externalUserIdPresent;

        /**
         * 初始化主体，保存构造参数供后续方法使用。
         */
        public Subject() {
        }

        /**
         * 构造器中的 null 表示字段缺失；Jackson setter 则能记录显式 null。
         *
         * @param type 类型依赖，保存到当前对象供后续业务方法调用
         * @param assertion 断言依赖，保存到当前对象供后续业务方法调用
         * @param namespace 命名空间依赖，保存到当前对象供后续业务方法调用
         * @param externalUserId 外部用户ID依赖，保存到当前对象供后续业务方法调用
         */
        public Subject(String type, String assertion, String namespace, String externalUserId) {
            this.type = type;
            this.assertion = assertion;
            this.namespace = namespace;
            this.externalUserId = externalUserId;
            this.assertionPresent = assertion != null;
            this.namespacePresent = namespace != null;
            this.externalUserIdPresent = externalUserId != null;
        }

        /**
         * 设置类型；后续读取或执行将使用更新后的状态。
         *
         * @param type 类型标识，决定后续类型采用的处理分支
         */
        @JsonSetter("type")
        public void setType(String type) {
            this.type = type;
        }

        /**
         * 设置断言；后续读取或执行将使用更新后的状态。
         *
         * @param assertion 断言，作为 {@code JsonSetter} 的输入影响后续处理
         */
        @JsonSetter("assertion")
        public void setAssertion(String assertion) {
            this.assertion = assertion;
            this.assertionPresent = true;
        }

        /**
         * 设置命名空间；后续读取或执行将使用更新后的状态。
         *
         * @param namespace 命名空间，作为 {@code JsonSetter} 的输入影响后续处理
         */
        @JsonSetter("namespace")
        public void setNamespace(String namespace) {
            this.namespace = namespace;
            this.namespacePresent = true;
        }

        /**
         * 设置外部用户ID；后续读取或执行将使用更新后的状态。
         *
         * @param externalUserId 外部用户ID，后续用于设置外部用户ID时定位或关联目标
         */
        @JsonSetter("externalUserId")
        public void setExternalUserId(String externalUserId) {
            this.externalUserId = externalUserId;
            this.externalUserIdPresent = true;
        }

        /**
         * 生成类型文本，供后续匹配或展示。
         *
         * @return 处理后的类型文本，供调用方比较或展示
         */
        public String type() {
            return type;
        }

        /**
         * 生成断言文本，供后续匹配或展示。
         *
         * @return 处理后的断言文本，供调用方比较或展示
         */
        public String assertion() {
            return assertion;
        }

        /**
         * 生成命名空间文本，供后续匹配或展示。
         *
         * @return 处理后的命名空间文本，供调用方比较或展示
         */
        public String namespace() {
            return namespace;
        }

        /**
         * 生成外部用户ID文本，供后续匹配或展示。
         *
         * @return 处理后的外部用户ID文本，供调用方比较或展示
         */
        public String externalUserId() {
            return externalUserId;
        }

        /**
         * 判断断言存在条件是否成立，供调用方选择后续分支。
         *
         * @return 断言存在条件成立时为 true，否则为 false
         */
        public boolean assertionPresent() {
            return assertionPresent;
        }

        /**
         * 判断命名空间存在条件是否成立，供调用方选择后续分支。
         *
         * @return 命名空间存在条件成立时为 true，否则为 false
         */
        public boolean namespacePresent() {
            return namespacePresent;
        }

        /**
         * 判断外部用户ID存在条件是否成立，供调用方选择后续分支。
         *
         * @return 外部用户ID存在条件成立时为 true，否则为 false
         */
        public boolean externalUserIdPresent() {
            return externalUserIdPresent;
        }

        /**
         * 处理驳回{@code unknown}字段，并将结果传给后续步骤。
         *
         * @param name 名称，后续用于处理驳回{@code unknown}字段时匹配或展示
         * @param value 待处理驳回{@code unknown}字段的原始输入，结果供调用方继续使用
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Launch subject contains unsupported fields");
        }
    }

    /**
     * Requested initial Embed surface and optional candidate record.
     */
    public static final class Entry {

        @JsonProperty("mode")
        @NotBlank
        @Pattern(regexp = "LIST|CREATE|VIEW")
        private String mode;
        @JsonProperty("recordId")
        @Size(max = 128)
        private String recordId;
        @JsonIgnore
        private boolean recordIdPresent;

        /**
         * 初始化入口，保存构造参数供后续方法使用。
         */
        public Entry() {
        }

        /**
         * 构造器中的 null 表示字段缺失；Jackson setter 则能记录显式 null。
         *
         * @param mode 模式依赖，保存到当前对象供后续业务方法调用
         * @param recordId 记录ID依赖，保存到当前对象供后续业务方法调用
         */
        public Entry(String mode, String recordId) {
            this.mode = mode;
            this.recordId = recordId;
            this.recordIdPresent = recordId != null;
        }

        /**
         * 设置模式；后续读取或执行将使用更新后的状态。
         *
         * @param mode 模式标识，决定后续模式采用的处理分支
         */
        @JsonSetter("mode")
        public void setMode(String mode) {
            this.mode = mode;
        }

        /**
         * 设置记录ID；后续读取或执行将使用更新后的状态。
         *
         * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
         */
        @JsonSetter("recordId")
        public void setRecordId(String recordId) {
            this.recordId = recordId;
            this.recordIdPresent = true;
        }

        /**
         * 生成模式文本，供后续匹配或展示。
         *
         * @return 处理后的模式文本，供调用方比较或展示
         */
        public String mode() {
            return mode;
        }

        /**
         * 记录ID；供后续追溯或审计使用。
         *
         * @return 记录后的ID文本，供调用方比较或展示
         */
        public String recordId() {
            return recordId;
        }

        /**
         * 记录ID存在；供后续追溯或审计使用。
         *
         * @return ID存在条件成立时为 true，否则为 false
         */
        public boolean recordIdPresent() {
            return recordIdPresent;
        }

        /**
         * 处理驳回{@code unknown}字段，并将结果传给后续步骤。
         *
         * @param name 名称，后续用于处理驳回{@code unknown}字段时匹配或展示
         * @param value 待处理驳回{@code unknown}字段的原始输入，结果供调用方继续使用
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Launch entry contains unsupported fields");
        }
    }

    /**
     * Optional presentation preferences; the published release remains final.
     *
     * @param locale {@code locale}，保存在对象中供后续校验、查询或展示
     * @param theme {@code theme}，保存在对象中供后续校验、查询或展示
     * @param formPresentation 表单展示，保存在对象中供后续校验、查询或展示
     */
    public record Ui(
            @Size(max = 32) String locale,
            @Pattern(regexp = "light|dark|system") String theme,
            @Pattern(regexp = "seamless|dialog") String formPresentation) {

        /**
         * Compatibility constructor for callers that rely on the default presentation.
         *
         * @param locale {@code locale}，保存在对象中供后续校验、查询或展示
         * @param theme {@code theme}，保存在对象中供后续校验、查询或展示
         */
        public Ui(String locale, String theme) {
            this(locale, theme, null);
        }

        /**
         * 处理驳回{@code unknown}字段，并将结果传给后续步骤。
         *
         * @param name 名称，后续用于处理驳回{@code unknown}字段时匹配或展示
         * @param value 待处理驳回{@code unknown}字段的原始输入，结果供调用方继续使用
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Launch UI contains unsupported fields");
        }
    }
}
