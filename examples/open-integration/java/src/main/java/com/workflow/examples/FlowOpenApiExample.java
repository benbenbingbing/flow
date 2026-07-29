package com.workflow.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public final class FlowOpenApiExample {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private FlowOpenApiExample() {
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = required("FLOW_BASE_URL")
                .replaceAll("/+$", "");
        String clientId = required("FLOW_CLIENT_ID");
        String clientSecret = required("FLOW_CLIENT_SECRET");
        String token = issueToken(
                baseUrl,
                clientId,
                clientSecret);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl
                        + "/api/open/v1/process-definitions?limit=20"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(
                request,
                HttpResponse.BodyHandlers.ofString());
        JsonNode document = requireSuccess(
                "list process definitions",
                response);
        JsonNode data = document.path("data");
        if (!data.isObject() || !data.path("items").isArray()) {
            throw new IllegalStateException(
                    "V1 response is missing data.items");
        }
        System.out.printf(
                "Java contract passed: %d process definitions, traceId=%s%n",
                data.path("items").size(),
                document.path("traceId").asText("missing"));
    }

    private static String issueToken(
            String baseUrl,
            String clientId,
            String clientSecret) throws Exception {
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret)
                        .getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=client_credentials&scope="
                + URLEncoder.encode(
                        "process.definition.read",
                        StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/oauth2/token"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type",
                        "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + credentials)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(
                request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "token endpoint returned HTTP "
                            + response.statusCode());
        }
        JsonNode document = JSON.readTree(response.body());
        String token = document.path("access_token").asText();
        if (token.isBlank()
                || !"Bearer".equals(
                document.path("token_type").asText())) {
            throw new IllegalStateException(
                    "token response does not match the V1 contract");
        }
        return token;
    }

    private static JsonNode requireSuccess(
            String operation,
            HttpResponse<String> response) throws Exception {
        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    operation + " returned HTTP "
                            + response.statusCode());
        }
        JsonNode document = JSON.readTree(response.body());
        if (document.path("code").asInt(-1) != 200
                || !document.path("errorCode").isNull()) {
            throw new IllegalStateException(
                    operation
                            + " returned an invalid V1 response");
        }
        return document;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " is required");
        }
        return value;
    }
}
