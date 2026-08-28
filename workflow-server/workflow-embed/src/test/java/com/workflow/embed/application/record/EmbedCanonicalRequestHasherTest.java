package com.workflow.embed.application.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmbedCanonicalRequestHasherTest {

    private final EmbedCanonicalRequestHasher hasher =
            new EmbedCanonicalRequestHasher(
                    new ObjectMapper().findAndRegisterModules());

    @Test
    void canonicalizesObjectKeysButPreservesArrayOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", Map.of("b", 2, "a", 1));
        first.put("items", List.of("A", "B"));
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("items", List.of("A", "B"));
        reordered.put("z", Map.of("a", 1, "b", 2));

        String actor = hasher.actorScopeDigest(session("binding-1", "session-1"));
        assertEquals(
                hash(actor, first),
                hash(actor, reordered));
        assertNotEquals(
                hash(actor, first),
                hash(actor, Map.of(
                        "z", Map.of("a", 1, "b", 2),
                        "items", List.of("B", "A"))));
    }

    @Test
    void stableActorScopeIgnoresSessionButBindsIdentityBinding() {
        assertEquals(
                hasher.actorScopeDigest(session("binding-1", "session-1")),
                hasher.actorScopeDigest(session("binding-1", "session-2")));
        assertNotEquals(
                hasher.actorScopeDigest(session("binding-1", "session-1")),
                hasher.actorScopeDigest(session("binding-2", "session-1")));
        assertThrows(IllegalStateException.class,
                () -> hasher.actorScopeDigest(new AuthenticatedEmbedSession(
                        "session-1", "app-1", "grant-1",
                        "view-1", "release-1", "user-1", "zhangsan",
                        "https://portal.example.com", "channel-1", "CREATE",
                        null, Map.of(), Set.of("RECORD_CREATE"),
                        Instant.parse("2026-08-27T05:00:00Z"),
                        Instant.parse("2026-08-27T06:00:00Z"))));
    }

    @Test
    void missingAndExplicitNullMutationIdsHaveDifferentBodies() {
        String actor = hasher.actorScopeDigest(session("binding-1", "session-1"));
        Map<String, Object> explicitNull = new LinkedHashMap<>();
        explicitNull.put("data", Map.of("title", "A"));
        explicitNull.put("clientMutationId", null);
        assertNotEquals(
                hash(actor, Map.of("data", Map.of("title", "A"))),
                hash(actor, explicitNull));
    }

    private String hash(String actor, Object body) {
        return hasher.requestHash(
                "app-1", actor, "view-key", "EMBED_RECORD_CREATE",
                "ENTITY", "work_order", body);
    }

    private static AuthenticatedEmbedSession session(
            String bindingId,
            String sessionId) {
        return new AuthenticatedEmbedSession(
                sessionId, "app-1", "grant-1", "provider-1", bindingId,
                "view-1", "release-1", "user-1", "zhangsan",
                "https://portal.example.com", "channel-1234567890", "CREATE",
                null, Map.of("supplier", "S-1"), Set.of("RECORD_CREATE"),
                Instant.parse("2026-08-27T05:00:00Z"),
                Instant.parse("2026-08-27T06:00:00Z"));
    }
}
