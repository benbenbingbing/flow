package com.workflow.openapi.api.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenEmbedLaunchRequestValidationTest {

    @Test
    void acceptsTheTwoExplicitSubjectShapes() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request(
                    new OpenEmbedLaunchRequest.Subject(
                            "SIGNED_JWT", "signed.jwt.value", null, null),
                    new OpenEmbedLaunchRequest.Entry("LIST", null)))
                    .isEmpty());
            assertTrue(validator.validate(request(
                    new OpenEmbedLaunchRequest.Subject(
                            "TRUSTED_EXTERNAL_ID", null, "erp-prod", "user-1"),
                    new OpenEmbedLaunchRequest.Entry("VIEW", "record-1")))
                    .isEmpty());
        }
    }

    @Test
    void acceptsAndMapsSupportedFormPresentationValues() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String value : new String[] {"seamless", "dialog"}) {
                OpenEmbedLaunchRequest request = request(value);
                assertTrue(validator.validate(request).isEmpty(), value);
                assertEquals(value, request.toCommand().ui().formPresentation());
            }
        }
    }

    @Test
    void rejectsBlankOrUnsupportedFormPresentationValues() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String value : new String[] {"", " ", "SEAMLESS", "drawer"}) {
                assertFalse(validator.validate(request(value)).isEmpty(), value);
            }
        }
    }

    @Test
    void rejectsMixedOrIncompleteSubjectAndEntryShapes() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(request(
                    new OpenEmbedLaunchRequest.Subject(
                            "SIGNED_JWT", null, null, null),
                    new OpenEmbedLaunchRequest.Entry("LIST", null)))
                    .isEmpty());
            assertFalse(validator.validate(request(
                    new OpenEmbedLaunchRequest.Subject(
                            "TRUSTED_EXTERNAL_ID", "must-not-be-present",
                            "erp-prod", "user-1"),
                    new OpenEmbedLaunchRequest.Entry("CREATE", "record-1")))
                    .isEmpty());
            assertFalse(validator.validate(request(
                    new OpenEmbedLaunchRequest.Subject(
                            "SIGNED_JWT", "signed.jwt.value", null, null),
                    new OpenEmbedLaunchRequest.Entry("VIEW", null)))
                    .isEmpty());
            assertFalse(validator.validate(request(
                    new OpenEmbedLaunchRequest.Subject(
                            "SIGNED_JWT", "signed.jwt.value", null, null),
                    new OpenEmbedLaunchRequest.Entry("EDIT", "record-1")))
                    .isEmpty());
        }
    }

    @Test
    void rejectsUnknownFieldsEvenWhenGlobalJacksonPolicyIsLenient() {
        ObjectMapper mapper = new ObjectMapper().configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        String base = """
                {
                  "viewKey":"supplier-work-orders",
                  "parentOrigin":"https://portal.partner.example",
                  "channelId":"66f82f09-89ec-4a5a-b81b-f54f02d22262",
                  "subject":{"type":"SIGNED_JWT","assertion":"signed.jwt.value"},
                  "entry":{"mode":"LIST"},
                  "context":{},
                  "ui":{"locale":"zh-CN","theme":"light"}%s
                }
                """;
        assertThrows(Exception.class, () -> mapper.readValue(
                base.formatted(",\"flowUserId\":\"attacker-selected\""),
                OpenEmbedLaunchRequest.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                base.formatted("").replace(
                        "\"assertion\":\"signed.jwt.value\"",
                        "\"assertion\":\"signed.jwt.value\",\"issuer\":\"ignored\""),
                OpenEmbedLaunchRequest.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                base.formatted("").replace(
                        "\"mode\":\"LIST\"",
                        "\"mode\":\"LIST\",\"releaseId\":\"ignored\""),
                OpenEmbedLaunchRequest.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                base.formatted("").replace(
                        "\"theme\":\"light\"",
                        "\"theme\":\"light\",\"script\":\"ignored\""),
                OpenEmbedLaunchRequest.class));
    }

    @Test
    void rejectsPresentFieldsFromAnotherSubjectBranchEvenWhenNullOrBlank()
            throws Exception {
        ObjectMapper mapper = mapper();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            for (String subject : new String[] {
                    "{\"type\":\"SIGNED_JWT\",\"assertion\":\"jwt\",\"namespace\":null}",
                    "{\"type\":\"SIGNED_JWT\",\"assertion\":\"jwt\",\"namespace\":\"\"}",
                    "{\"type\":\"SIGNED_JWT\",\"assertion\":\"jwt\",\"externalUserId\":null}",
                    "{\"type\":\"SIGNED_JWT\",\"assertion\":\"jwt\",\"externalUserId\":\" \"}",
                    "{\"type\":\"TRUSTED_EXTERNAL_ID\",\"assertion\":null,"
                            + "\"namespace\":\"erp\",\"externalUserId\":\"u-1\"}",
                    "{\"type\":\"TRUSTED_EXTERNAL_ID\",\"assertion\":\"\","
                            + "\"namespace\":\"erp\",\"externalUserId\":\"u-1\"}"
            }) {
                assertFalse(validator.validate(mapper.readValue(
                        json(subject, "{\"mode\":\"LIST\"}"),
                        OpenEmbedLaunchRequest.class)).isEmpty(), subject);
            }
        }
    }

    @Test
    void rejectsMissingNullAndBlankRequiredDiscriminatorFields() throws Exception {
        ObjectMapper mapper = mapper();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            for (String subject : new String[] {
                    "{\"type\":\"SIGNED_JWT\"}",
                    "{\"type\":\"SIGNED_JWT\",\"assertion\":null}",
                    "{\"type\":\"SIGNED_JWT\",\"assertion\":\" \"}",
                    "{\"type\":\"TRUSTED_EXTERNAL_ID\",\"externalUserId\":\"u-1\"}",
                    "{\"type\":\"TRUSTED_EXTERNAL_ID\",\"namespace\":null,\"externalUserId\":\"u-1\"}",
                    "{\"type\":\"TRUSTED_EXTERNAL_ID\",\"namespace\":\"\",\"externalUserId\":\"u-1\"}",
                    "{\"type\":\"TRUSTED_EXTERNAL_ID\",\"namespace\":\"erp\"}",
                    "{\"type\":\"TRUSTED_EXTERNAL_ID\",\"namespace\":\"erp\",\"externalUserId\":null}",
                    "{\"type\":\"TRUSTED_EXTERNAL_ID\",\"namespace\":\"erp\",\"externalUserId\":\" \"}"
            }) {
                assertFalse(validator.validate(mapper.readValue(
                        json(subject, "{\"mode\":\"LIST\"}"),
                        OpenEmbedLaunchRequest.class)).isEmpty(), subject);
            }
        }
    }

    @Test
    void entryRecordIdPresenceExactlyMatchesMode() throws Exception {
        ObjectMapper mapper = mapper();
        String subject = "{\"type\":\"SIGNED_JWT\",\"assertion\":\"jwt\"}";
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            for (String entry : new String[] {
                    "{\"mode\":\"LIST\",\"recordId\":null}",
                    "{\"mode\":\"LIST\",\"recordId\":\"\"}",
                    "{\"mode\":\"CREATE\",\"recordId\":null}",
                    "{\"mode\":\"CREATE\",\"recordId\":\" \"}",
                    "{\"mode\":\"VIEW\"}",
                    "{\"mode\":\"VIEW\",\"recordId\":null}",
                    "{\"mode\":\"VIEW\",\"recordId\":\"\"}"
            }) {
                assertFalse(validator.validate(mapper.readValue(
                        json(subject, entry), OpenEmbedLaunchRequest.class))
                        .isEmpty(), entry);
            }
        }
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static String json(String subject, String entry) {
        return """
                {
                  "viewKey":"supplier-work-orders",
                  "parentOrigin":"https://portal.partner.example",
                  "channelId":"66f82f09-89ec-4a5a-b81b-f54f02d22262",
                  "subject":%s,
                  "entry":%s
                }
                """.formatted(subject, entry);
    }

    private OpenEmbedLaunchRequest request(
            OpenEmbedLaunchRequest.Subject subject,
            OpenEmbedLaunchRequest.Entry entry) {
        return new OpenEmbedLaunchRequest(
                "supplier-work-orders",
                "https://portal.partner.example",
                "66f82f09-89ec-4a5a-b81b-f54f02d22262",
                subject,
                entry,
                Map.of("supplierId", "S-10086"),
                new OpenEmbedLaunchRequest.Ui("zh-CN", "light"));
    }

    private OpenEmbedLaunchRequest request(String formPresentation) {
        return new OpenEmbedLaunchRequest(
                "supplier-work-orders",
                "https://portal.partner.example",
                "66f82f09-89ec-4a5a-b81b-f54f02d22262",
                new OpenEmbedLaunchRequest.Subject(
                        "SIGNED_JWT", "signed.jwt.value", null, null),
                new OpenEmbedLaunchRequest.Entry("LIST", null),
                Map.of("supplierId", "S-10086"),
                new OpenEmbedLaunchRequest.Ui("zh-CN", "light", formPresentation));
    }
}
