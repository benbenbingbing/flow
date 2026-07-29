package com.workflow.openapi.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntegrationVariableSchemaServiceTest {

    private ObjectMapper objectMapper;
    private IntegrationVariableSchemaService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new IntegrationVariableSchemaService(objectMapper);
    }

    @Test
    void validatesVariablesAgainstAClosedSchema() throws Exception {
        String schema = service.validateConfiguration(
                objectMapper.readTree("""
                        {
                          "type": "object",
                          "maxProperties": 2,
                          "additionalProperties": false,
                          "required": ["title", "riskLevel"],
                          "properties": {
                            "title": {
                              "type": "string",
                              "maxLength": 200
                            },
                            "riskLevel": {
                              "type": "string",
                              "enum": ["LOW", "HIGH"]
                            }
                          }
                        }
                        """));

        assertTrue(service.validateVariables(
                schema,
                Map.of(
                        "title", "Release",
                        "riskLevel", "HIGH")).isEmpty());
        var violations = service.validateVariables(
                schema,
                Map.of(
                        "title", "Release",
                        "riskLevel", "UNKNOWN"));
        assertEquals(1, violations.size());
    }

    @Test
    void rejectsRemoteReferencesAndRegexConstraints()
            throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateConfiguration(
                        objectMapper.readTree("""
                                {
                                  "type": "object",
                                  "maxProperties": 1,
                                  "additionalProperties": false,
                                  "properties": {
                                    "value": {
                                      "$ref": "https://example.test/schema"
                                    }
                                  }
                                }
                                """)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateConfiguration(
                        objectMapper.readTree("""
                                {
                                  "type": "object",
                                  "maxProperties": 1,
                                  "additionalProperties": false,
                                  "properties": {
                                    "value": {
                                      "type": "string",
                                      "pattern": "(a+)+$"
                                    }
                                  }
                                }
                                """)));
    }

    @Test
    void rejectsEngineReservedVariablesAndOpenObjects()
            throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateConfiguration(
                        objectMapper.readTree("""
                                {
                                  "type": "object",
                                  "maxProperties": 1,
                                  "additionalProperties": false,
                                  "properties": {
                                    "initiator": {
                                      "type": "string"
                                    }
                                  }
                                }
                                """)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateConfiguration(
                        objectMapper.readTree("""
                                {
                                  "type": "object",
                                  "maxProperties": 10,
                                  "additionalProperties": true
                                }
                                """)));
    }
}
