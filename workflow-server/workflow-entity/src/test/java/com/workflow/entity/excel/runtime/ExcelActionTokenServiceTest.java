package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ExcelActionTokenServiceTest {
    private final ExcelActionTokenService service = new ExcelActionTokenService(new ObjectMapper(), "excel-test-secret");
    @BeforeEach void login() { UserContext.setCurrentUser("alice", "alice"); }
    @AfterEach void logout() { UserContext.clear(); }
    private ExcelActionTokenService.Claims claims(String operation, long expires, List<String> allowed) {
        var origin = new UiEventExecuteRequest(); origin.setConfigType("FORM"); origin.setConfigId("form1");
        origin.setTargetType("BUTTON"); origin.setTargetKey("excel"); origin.setEntityCode("employee"); origin.setEventCode("FORM_BUTTON_CLICK");
        return new ExcelActionTokenService.Claims(1, "alice", expires, "CUSTOM", origin, "source",
                operation, "CURRENT_RECORD", "EXPLICIT", "basic", "scheme", "", "entity", "form", allowed);
    }
    @Test void importTokenCanCheckAndDownloadTemplateButCannotExport() {
        String token = service.issue(claims("IMPORT", Instant.now().getEpochSecond() + 60, List.of("IMPORT", "CHECK", "TEMPLATE")));
        assertThat(service.verify(token, "IMPORT").userId()).isEqualTo("alice");
        assertThat(service.verify(token, "CHECK").operation()).isEqualTo("IMPORT");
        assertThat(service.verify(token, "TEMPLATE").operation()).isEqualTo("IMPORT");
        assertThatThrownBy(() -> service.verify(token, "EXPORT")).isInstanceOf(ForbiddenException.class);
    }
    @Test void templateTokenNeverGrantsWriteOrCheck() {
        String token = service.issue(claims("TEMPLATE", Instant.now().getEpochSecond() + 60, List.of("TEMPLATE")));
        assertThat(service.verify(token, "TEMPLATE").operation()).isEqualTo("TEMPLATE");
        assertThatThrownBy(() -> service.verify(token, "IMPORT")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.verify(token, "CHECK")).isInstanceOf(ForbiddenException.class);
    }
    @Test void rejectsExpiredTokenAndCrossUserReplay() {
        String expired = service.issue(claims("EXPORT", Instant.now().getEpochSecond() - 1, List.of("EXPORT")));
        assertThatThrownBy(() -> service.verify(expired, "EXPORT")).isInstanceOf(ForbiddenException.class);
        String token = service.issue(claims("EXPORT", Instant.now().getEpochSecond() + 60, List.of("EXPORT")));
        UserContext.setCurrentUser("bob", "bob");
        assertThatThrownBy(() -> service.verify(token, "EXPORT")).isInstanceOf(ForbiddenException.class);
        UserContext.clear();
        assertThatThrownBy(() -> service.verify(token, "EXPORT")).isInstanceOf(ForbiddenException.class);
    }
    @Test void rejectsTamperingWrongDeploymentSecretAndMalformedTokens() {
        String token = service.issue(claims("EXPORT", Instant.now().getEpochSecond() + 60, List.of("EXPORT")));
        String tampered = (token.startsWith("a") ? "b" : "a") + token.substring(1);
        assertThatThrownBy(() -> service.verify(tampered, "EXPORT")).isInstanceOf(ForbiddenException.class);
        var other = new ExcelActionTokenService(new ObjectMapper(), "another-secret");
        assertThatThrownBy(() -> other.verify(token, "EXPORT")).isInstanceOf(ForbiddenException.class);
        for (String invalid : List.of("", "a.b", "a.b.c", "abc", ".")) {
            assertThatThrownBy(() -> service.verify(invalid, "EXPORT")).isInstanceOf(ForbiddenException.class);
        }
    }
    @Test void configurationHashIgnoresMapOrderingButDetectsChanges() {
        var first = new LinkedHashMap<String, Object>(); first.put("a", 1); first.put("b", Map.of("x", "value"));
        var second = new LinkedHashMap<String, Object>(); second.put("b", Map.of("x", "value")); second.put("a", 1);
        assertThat(service.hash(first)).isEqualTo(service.hash(second));
        second.put("a", 2); assertThat(service.hash(first)).isNotEqualTo(service.hash(second));
    }

    @Test void rejectsIncompleteSignedOriginAndInconsistentOperationGrants() {
        var incomplete = claims("EXPORT", Instant.now().getEpochSecond() + 60, List.of("EXPORT"));
        incomplete.origin().setConfigId(null);
        assertThatThrownBy(() -> service.verify(service.issue(incomplete), "EXPORT")).isInstanceOf(ForbiddenException.class);
        var broadened = claims("TEMPLATE", Instant.now().getEpochSecond() + 60, List.of("TEMPLATE", "IMPORT"));
        assertThatThrownBy(() -> service.verify(service.issue(broadened), "IMPORT")).isInstanceOf(ForbiddenException.class);
        var mismatchedEvent = claims("EXPORT", Instant.now().getEpochSecond() + 60, List.of("EXPORT"));
        mismatchedEvent.origin().setEventCode("TOOLBAR_BUTTON_CLICK");
        assertThatThrownBy(() -> service.verify(service.issue(mismatchedEvent), "EXPORT")).isInstanceOf(ForbiddenException.class);
    }
}
