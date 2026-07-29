package com.workflow.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HttpConnectorConfigurationCodecTest {

    private final HttpConnectorConfigurationCodec codec =
            new HttpConnectorConfigurationCodec(new ObjectMapper());

    @Test
    void readsStrictDeclarativeConfiguration() {
        HttpConnectorConfiguration configuration = codec.read(
                "config-1",
                "app-1",
                """
                {
                  "baseUrl": "https://erp.example.com/api",
                  "operations": {
                    "sync-order": {
                      "method": "POST",
                      "path": "/orders",
                      "query": {
                        "dryRun": "$input.dryRun"
                      },
                      "headers": {
                        "X-Tenant": "$context.tenantId"
                      },
                      "body": {
                        "/orderId": "$input.orderId"
                      },
                      "response": {
                        "remoteId": "/data/id"
                      },
                      "acceptedStatuses": [200, 201],
                      "authentication": {
                        "type": "BEARER",
                        "secretRef":
                          "secret://integration/app-1/api-token"
                      },
                      "timeoutMs": 4000,
                      "maxAttempts": 2
                    }
                  }
                }
                """,
                "[\"erp.example.com\"]");

        assertEquals(
                "erp.example.com",
                configuration.baseUri().getHost());
        var operation = configuration.operations().get("sync-order");
        assertEquals("POST", operation.method());
        assertEquals(2, operation.maxAttempts());
        assertEquals(
                HttpConnectorConfiguration.Authentication.Type.BEARER,
                operation.authentication().type());
    }

    @Test
    void rejectsCrossApplicationAndPlaintextAuthentication() {
        assertInvalid(configuration("""
                {
                  "type": "BEARER",
                  "secretRef": "secret://integration/app-2/api-token"
                }
                """));
        assertInvalid(configuration("""
                {
                  "type": "BEARER",
                  "secretRef": "plain-token"
                }
                """));
    }

    @Test
    void rejectsWildcardIpAndUnlistedDestinations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.read(
                        "config-1",
                        "app-1",
                        configuration("{\"type\":\"NONE\"}"),
                        "[\"*.example.com\"]"));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.read(
                        "config-1",
                        "app-1",
                        configuration("{\"type\":\"NONE\"}"),
                        "[\"127.0.0.1\"]"));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.read(
                        "config-1",
                        "app-1",
                        configuration("{\"type\":\"NONE\"}")
                                .replace(
                                        "erp.example.com",
                                        "other.example.com"),
                        "[\"erp.example.com\"]"));
    }

    @Test
    void rejectsDynamicPathsProtectedHeadersAndUnknownFields() {
        assertInvalid(operation("""
                "path": "/orders/${id}"
                """));
        assertInvalid(operation("""
                "headers": {"Authorization": "$input.token"}
                """));
        assertInvalid(operation("""
                "script": "return process.env.SECRET"
                """));
    }

    @Test
    void rejectsBodiesOnGetAndNonSuccessAllowlist() {
        assertInvalid(operation("""
                "method": "GET",
                "body": {"/id": "$input.id"}
                """));
        assertInvalid(operation("""
                "acceptedStatuses": [200, 500]
                """));
    }

    @Test
    void rejectsUnknownContextAndMalformedInputSources() {
        assertInvalid(operation("""
                "headers": {"X-Actor": "$context.username"}
                """));
        assertInvalid(operation("""
                "query": {"actor": "$input.actor..id"}
                """));
        assertInvalid(operation("""
                "query": {"actor": "$context.tenantId.extra"}
                """));
    }

    @Test
    void rejectsMalformedDeepAndConflictingJsonPointers() {
        assertInvalid(operation("""
                "body": {"/bad~2key": "$input.id"}
                """));
        assertInvalid(operation("""
                "body": {"/customer": "$input.customer",
                         "/customer/id": "$input.customer.id"}
                """));
        assertInvalid(operation("""
                "body": {"/a//b": "$input.id"}
                """));
        assertInvalid(operation("""
                "response": {"value":
                  "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q"}
                """));
    }

    @Test
    void rejectsConfigurationDocumentsOverStorageLimit() {
        String oversized = """
                {"baseUrl":"https://erp.example.com/api",
                 "operations":{"lookup":{"method":"GET","path":"/orders",
                 "authentication":{"type":"NONE"}}},
                 "padding":"%s"}
                """.formatted("x".repeat(256 * 1024));

        assertInvalid(oversized);
    }

    private void assertInvalid(String document) {
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.read(
                        "config-1",
                        "app-1",
                        document,
                        "[\"erp.example.com\"]"));
    }

    private String configuration(String authentication) {
        return """
                {
                  "baseUrl": "https://erp.example.com/api",
                  "operations": {
                    "lookup": {
                      "method": "GET",
                      "path": "/orders",
                      "authentication": %s
                    }
                  }
                }
                """.formatted(authentication);
    }

    private String operation(String extra) {
        return """
                {
                  "baseUrl": "https://erp.example.com/api",
                  "operations": {
                    "lookup": {
                      "method": "POST",
                      "path": "/orders",
                      %s,
                      "authentication": {"type": "NONE"}
                    }
                  }
                }
                """.formatted(extra);
    }
}
