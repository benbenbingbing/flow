package com.workflow.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestServiceTaskDelegateTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsConfiguredRequestAndMapsJsonResponse() throws Exception {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> requestQuery = new AtomicReference<>();
        AtomicReference<String> requestHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/workflow-test", exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            requestQuery.set(exchange.getRequestURI().getRawQuery());
            requestHeader.set(exchange.getRequestHeaders().getFirst(
                    "X-Business-Ref"));
            requestBody.set(readBody(exchange));
            respond(exchange, 200, "{\"data\":{\"id\":42},\"status\":\"accepted\"}");
        });
        server.start();

        String config = """
                {
                  "url":"http://127.0.0.1:%d/workflow-test",
                  "method":"POST",
                  "headers":"{\\"X-Business-Ref\\":\\"${businessRef}\\"}",
                  "queryParams":"{\\"businessNo\\":\\"${businessNo}\\"}",
                  "body":"{\\"amount\\":${amount}}",
                  "contentType":"application/json",
                  "timeout":5,
                  "retryCount":0,
                  "errorHandling":"throw",
                  "resultMapping":"{\\"data.id\\":\\"remoteId\\",\\"status\\":\\"remoteStatus\\"}"
                }
                """.formatted(server.getAddress().getPort());
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getCurrentFlowElement()).thenReturn(serviceTask("restConfig", config));
        when(execution.getVariable("businessRef")).thenReturn("BX-1001");
        when(execution.getVariable("businessNo")).thenReturn("BX 1001");
        when(execution.getVariable("amount")).thenReturn(125.5);
        when(execution.getProcessInstanceId()).thenReturn("instance-1");
        when(execution.getCurrentActivityId()).thenReturn("rest-task-1");

        newDelegate().execute(execution);

        assertEquals("POST", requestMethod.get());
        assertEquals("businessNo=BX+1001", requestQuery.get());
        assertEquals("BX-1001", requestHeader.get());
        assertEquals("{\"amount\":125.5}", requestBody.get());
        verify(execution).setVariable("remoteId", 42);
        verify(execution).setVariable("remoteStatus", "accepted");
        verify(execution).setVariable("rest-task-1_httpStatus", 200);
    }

    @Test
    void rejectsResponsesAboveConfiguredLimit()
            throws Exception {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0);
        server.createContext("/large", exchange ->
                respond(exchange, 200, "x".repeat(1025)));
        server.start();
        String config = """
                {
                  "url":"http://127.0.0.1:%d/large",
                  "method":"GET",
                  "timeout":5
                }
                """.formatted(server.getAddress().getPort());
        DelegateExecution execution =
                mock(DelegateExecution.class);
        when(execution.getCurrentFlowElement())
                .thenReturn(serviceTask("restConfig", config));
        when(execution.getProcessInstanceId())
                .thenReturn("instance-1");
        when(execution.getCurrentActivityId())
                .thenReturn("rest-task-large");
        WorkflowHttpProperties properties =
                testProperties();
        properties.setMaxResponseBytes(1024);
        RestServiceTaskDelegate delegate =
                new RestServiceTaskDelegate(
                        new ObjectMapper(),
                        new RestEndpointPolicy(properties),
                        properties);

        assertThrows(
                IllegalStateException.class,
                () -> delegate.execute(execution));
    }

    @Test
    void rejectsDynamicHostsBeforeNetworkAccess() {
        String config = """
                {
                  "url":"https://${targetHost}/orders",
                  "method":"GET"
                }
                """;
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getCurrentFlowElement())
                .thenReturn(serviceTask("restConfig", config));
        when(execution.getVariable("targetHost"))
                .thenReturn("169.254.169.254");

        assertThrows(
                IllegalArgumentException.class,
                () -> newDelegate().execute(execution));
    }

    @Test
    void rejectsCredentialHeadersInLegacyRestTasks() {
        String config = """
                {
                  "url":"https://example.com/orders",
                  "method":"GET",
                  "headers":"{\\"Authorization\\":\\"Bearer ${token}\\"}"
                }
                """;
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getCurrentFlowElement())
                .thenReturn(serviceTask("restConfig", config));
        when(execution.getVariable("token"))
                .thenReturn("plaintext-secret");
        when(execution.getProcessInstanceId()).thenReturn("instance-1");
        when(execution.getCurrentActivityId()).thenReturn("rest-task-1");

        assertThrows(
                IllegalArgumentException.class,
                () -> newDelegate().execute(execution));
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private RestServiceTaskDelegate newDelegate() {
        WorkflowHttpProperties properties = testProperties();
        return new RestServiceTaskDelegate(
                new ObjectMapper(),
                new RestEndpointPolicy(properties),
                properties);
    }

    private WorkflowHttpProperties testProperties() {
        WorkflowHttpProperties properties =
                new WorkflowHttpProperties();
        properties.setAllowedHosts(
                java.util.List.of("127.0.0.1"));
        properties.setAllowHttp(true);
        properties.setAllowPrivateAddresses(true);
        return properties;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private ServiceTask serviceTask(String propertyName, String propertyValue) {
        ServiceTask task = new ServiceTask();
        ExtensionElement properties = extensionElement("properties");
        properties.addChildElement(property(propertyName, propertyValue));
        task.addExtensionElement(properties);
        return task;
    }

    private ExtensionElement property(String name, String value) {
        ExtensionElement property = extensionElement("property");
        property.addAttribute(new ExtensionAttribute("name", name));
        property.addAttribute(new ExtensionAttribute("value", value));
        return property;
    }

    private ExtensionElement extensionElement(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        element.setNamespace("http://flowable.org/bpmn");
        element.setNamespacePrefix("flowable");
        return element;
    }
}
