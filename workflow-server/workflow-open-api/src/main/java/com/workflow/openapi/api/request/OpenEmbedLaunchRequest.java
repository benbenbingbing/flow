package com.workflow.openapi.api.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.workflow.contracts.embed.EmbedLaunchCommand;
import com.workflow.contracts.embed.EmbedLaunchEntry;
import com.workflow.contracts.embed.EmbedLaunchSubject;
import com.workflow.contracts.embed.EmbedLaunchUi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * HTTP request for creating a one-time Embed launch.
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
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Launch request contains unsupported fields");
    }

    /**
     * 人员凭据按类型使用互斥字段，避免把缺失断言推迟到业务层才发现。
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
                ui == null ? null : new EmbedLaunchUi(ui.locale, ui.theme));
    }

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

        public Subject() {
        }

        /** 构造器中的 null 表示字段缺失；Jackson setter 则能记录显式 null。 */
        public Subject(String type, String assertion, String namespace, String externalUserId) {
            this.type = type;
            this.assertion = assertion;
            this.namespace = namespace;
            this.externalUserId = externalUserId;
            this.assertionPresent = assertion != null;
            this.namespacePresent = namespace != null;
            this.externalUserIdPresent = externalUserId != null;
        }

        @JsonSetter("type")
        public void setType(String type) {
            this.type = type;
        }

        @JsonSetter("assertion")
        public void setAssertion(String assertion) {
            this.assertion = assertion;
            this.assertionPresent = true;
        }

        @JsonSetter("namespace")
        public void setNamespace(String namespace) {
            this.namespace = namespace;
            this.namespacePresent = true;
        }

        @JsonSetter("externalUserId")
        public void setExternalUserId(String externalUserId) {
            this.externalUserId = externalUserId;
            this.externalUserIdPresent = true;
        }

        public String type() {
            return type;
        }

        public String assertion() {
            return assertion;
        }

        public String namespace() {
            return namespace;
        }

        public String externalUserId() {
            return externalUserId;
        }

        public boolean assertionPresent() {
            return assertionPresent;
        }

        public boolean namespacePresent() {
            return namespacePresent;
        }

        public boolean externalUserIdPresent() {
            return externalUserIdPresent;
        }

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

        public Entry() {
        }

        /** 构造器中的 null 表示字段缺失；Jackson setter 则能记录显式 null。 */
        public Entry(String mode, String recordId) {
            this.mode = mode;
            this.recordId = recordId;
            this.recordIdPresent = recordId != null;
        }

        @JsonSetter("mode")
        public void setMode(String mode) {
            this.mode = mode;
        }

        @JsonSetter("recordId")
        public void setRecordId(String recordId) {
            this.recordId = recordId;
            this.recordIdPresent = true;
        }

        public String mode() {
            return mode;
        }

        public String recordId() {
            return recordId;
        }

        public boolean recordIdPresent() {
            return recordIdPresent;
        }

        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Launch entry contains unsupported fields");
        }
    }

    /**
     * Optional presentation preferences; the published release remains final.
     */
    public record Ui(
            @Size(max = 32) String locale,
            @Pattern(regexp = "light|dark|system") String theme) {

        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Launch UI contains unsupported fields");
        }
    }
}
