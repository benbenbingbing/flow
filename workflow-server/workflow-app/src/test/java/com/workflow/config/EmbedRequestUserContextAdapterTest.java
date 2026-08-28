package com.workflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.workflow.admin.security.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EmbedRequestUserContextAdapterTest {

    private final EmbedRequestUserContextAdapter adapter =
            new EmbedRequestUserContextAdapter();

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void opensAndIdempotentlyClearsEmbedActor() {
        var scope = adapter.open("user-1", "alice", "ems-1");
        assertEquals("user-1", UserContext.getUserId());
        assertEquals("alice", UserContext.getUsername());
        assertEquals("ems-1", UserContext.getSessionId());

        scope.close();
        scope.close();
        assertNull(UserContext.getUserId());
        assertNull(UserContext.getUsername());
        assertNull(UserContext.getSessionId());
    }

    @Test
    void restoresOuterContextForNestedExecution() {
        UserContext.setCurrentUser("admin-1", "admin", "auth-session-1");
        try (var ignored = adapter.open("user-1", "alice", "ems-1")) {
            assertEquals("user-1", UserContext.getUserId());
        }
        assertEquals("admin-1", UserContext.getUserId());
        assertEquals("admin", UserContext.getUsername());
        assertEquals("auth-session-1", UserContext.getSessionId());
    }
}
