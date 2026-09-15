package com.workflow.migration.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 验证真实 BPMN/JSON 的定点转换、动态规则保留和人员引用图，不执行人员接口。 */
class ConfigMigrationAssignmentSupportTest {
    @Test
    void mapsUsersGroupsAndRolesWithoutReplacingDocumentationOrExpressions() {
        String xml = bpmn("""
                <bpmn:userTask id="review" flowable:assignee="source-login"
                    flowable:candidateUsers="source-login,other-login"
                    flowable:candidateGroups="finance,ROLE_manager">
                  <bpmn:documentation>source-login finance ROLE_manager</bpmn:documentation>
                </bpmn:userTask>
                <bpmn:userTask id="dynamic" flowable:assignee="${selector.pick('source-login')}" />
                """);
        List<String> refs = new ArrayList<>();
        String mapped = ConfigMigrationAssignmentSupport.rewriteBpmn(xml, "approval", (type, key, context) -> {
            refs.add(type + ":" + key);
            return "target-" + key;
        });
        assertTrue(mapped.contains("assignee=\"target-source-login\""));
        assertTrue(mapped.contains("target-finance,ROLE_target-manager"));
        assertTrue(mapped.contains(">source-login finance ROLE_manager</bpmn:documentation>"));
        assertTrue(mapped.contains("${selector.pick('source-login')}"));
        assertTrue(refs.containsAll(List.of("USER:source-login", "USER:other-login", "GROUP:finance", "ROLE:manager")));
        assertFalse(refs.contains("ROLE:finance"));
        assertFalse(refs.stream().anyMatch(value -> value.contains("selector")));
    }

    @Test
    void preservesMultiInstanceOrderAndMapsScopeRulesInBothDocuments() {
        Map<String, Object> config = Map.of("assignmentConfigVersion", 2, "assigneeType", "user",
                "assigneeValue", "bob", "candidateUsers", "bob,alice",
                "multiInstanceDecision", "countersign", "nextApproverSelection", Map.of(
                        "visible", true, "source", Map.of("type", "SCOPE", "rules", List.of(
                                Map.of("type", "USER", "values", List.of("alice")),
                                Map.of("type", "GROUP", "values", List.of("finance")),
                                Map.of("type", "ROLE", "values", List.of("manager")),
                                Map.of("type", "ORGANIZATION", "values", List.of("hq"))))));
        var mapper = (ConfigMigrationAssignmentSupport.ReferenceMapper) (type, key, context) -> "target-" + key;
        String json = ConfigMigrationAssignmentSupport.rewriteNodeConfig(
                ConfigMigrationAssignmentSupport.write(Map.of("assigneeConfig", config)), Map.of(), mapper);
        assertTrue(json.contains("bob") && json.contains("countersign"));
        assertTrue(json.contains("target-bob,target-alice"));
        assertTrue(json.contains("target-finance") && json.contains("target-hq"));
        String rewritten = ConfigMigrationAssignmentSupport.rewriteBpmn(
                bpmn(task("review", config, true)), "approval", mapper);
        String roundTrip = ConfigMigrationAssignmentSupport.rewriteBpmn(rewritten, "approval",
                (type, key, context) -> key.substring("target-".length()));
        assertTrue(roundTrip.contains("bob,alice"));
        assertFalse(roundTrip.contains("target-"));
        assertTrue(roundTrip.contains("multiInstanceLoopCharacteristics"));
    }

    @Test
    void extractsTypedResolverParametersAndEveryUseWithoutResolvingMembers() {
        Map<String, Object> relative = resolver("relativeOrgPosition", Map.of(
                "positionCode", "MANAGER", "hierarchy", Map.of("mode", "BUSINESS_LEVEL", "businessLevelCode", "COMPANY")));
        Map<String, Object> entity = resolver("entityUserReferenceField", Map.of("entityCode", "expense", "fieldCode", "reviewers"));
        Map<String, Object> custom = resolver("customPeople", Map.of("businessText", "admin"));
        List<Map<String, Object>> dependencies = new ArrayList<>();
        ConfigMigrationAssignmentSupport.rewriteBpmn(bpmn(task("a", relative, false)
                + task("b", entity, false) + task("c", custom, true)
                + task("d", custom, false)), "approval", (type, key, context) -> {
                    dependencies.add(Map.of("type", type, "key", key, "references", List.of(context)));
                    return key;
                });
        List<String> refs = dependencies.stream().map(value -> value.get("type") + ":" + value.get("key")).toList();
        assertTrue(refs.containsAll(List.of("POSITION:MANAGER", "ORG_BUSINESS_LEVEL:COMPANY",
                "ENTITY_USER_FIELD:expense/reviewers", "ENTITY:expense", "PERSON_RESOLVER:customPeople")));
        assertFalse(refs.contains("USER:admin"));
        var customDependency = ConfigMigrationAssignmentSupport.mergeDependencies(dependencies).stream()
                .filter(value -> "customPeople".equals(value.get("key"))).findFirst().orElseThrow();
        var usages = ConfigMigrationAssignmentSupport.maps(customDependency.get("references")).stream()
                .map(value -> value.get("usage")).toList();
        assertEquals(List.of("MULTI_INSTANCE", "ASSIGNEE"), usages);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "a"})
    void rejectsMissingAndCyclicNodeReferences(String referencedNode) {
        assertThrows(IllegalArgumentException.class, () -> ConfigMigrationAssignmentSupport.validateBpmn(
                bpmn(task("a", Map.of("assigneeType", "node_reference", "referencedNodeId", referencedNode), false))));
    }

    @Test
    void validatesExpressionSyntaxWithoutExecutingIt() {
        assertDoesNotThrow(() -> ConfigMigrationAssignmentSupport.validateBpmn(
                bpmn("<bpmn:userTask id=\"a\" flowable:assignee=\"${unknownService.neverExecute()}\" />")));
        assertDoesNotThrow(() -> ConfigMigrationAssignmentSupport.validateBpmn(
                bpmn("<bpmn:userTask id=\"a\" flowable:assignee=\"${runtime:findUser('admin')}\" />")));
        assertThrows(IllegalArgumentException.class, () -> ConfigMigrationAssignmentSupport.validateBpmn(
                bpmn("<bpmn:userTask id=\"a\" flowable:assignee=\"${broken(}\" />")));
    }

    @Test
    void scopedOwnerOverrideRetainsAllReferenceLocations() {
        var merged = ConfigMigrationAssignmentSupport.mergeDependencies(List.of(
                Map.of("type", "ENTITY", "key", "expense", "required", true,
                        "references", List.of(Map.of("nodeId", "review"))),
                Map.of("type", "ENTITY", "key", "expense", "required", true, "targetOnly", true)));
        assertEquals(true, merged.get(0).get("targetOnly"));
        assertEquals(1, ConfigMigrationAssignmentSupport.maps(merged.get(0).get("references")).size());
    }

    @Test
    void rejectsExternalEntityDocuments() {
        assertThrows(IllegalArgumentException.class, () -> ConfigMigrationAssignmentSupport.rewriteBpmn(
                "<!DOCTYPE x [<!ENTITY external SYSTEM 'file:///tmp/never-read'>]><x>&external;</x>",
                "approval", (type, key, context) -> key));
    }

    static Map<String, Object> resolver(String code, Map<String, Object> params) {
        return Map.of("assignmentConfigVersion", 2, "assigneeType", "interface", "resolverCode", code,
                "assignmentMode", "DIRECT", "extraParams", params);
    }

    static String task(String id, Map<String, Object> config, boolean multi) {
        String value = ConfigMigrationAssignmentSupport.write(config).replace("&", "&amp;").replace("\"", "&quot;");
        return "<bpmn:userTask id=\"" + id + "\" name=\"审核\"><bpmn:extensionElements><flowable:properties>"
                + "<flowable:property name=\"assigneeConfig\" value=\"" + value + "\" />"
                + "</flowable:properties></bpmn:extensionElements>"
                + (multi ? "<bpmn:multiInstanceLoopCharacteristics isSequential=\"true\"/>" : "")
                + "</bpmn:userTask>";
    }

    static String bpmn(String tasks) {
        return "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:flowable=\"http://flowable.org/bpmn\"><bpmn:process id=\"approval\">"
                + tasks + "</bpmn:process></bpmn:definitions>";
    }
}
