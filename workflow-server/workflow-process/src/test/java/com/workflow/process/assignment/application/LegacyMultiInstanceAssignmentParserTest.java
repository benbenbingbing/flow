package com.workflow.process.assignment.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyMultiInstanceAssignmentParserTest {

    @Test
    void mergesEveryHistoricalStaticFieldWithoutLosingOrder() {
        var legacy = LegacyMultiInstanceAssignmentParser.parse(Map.of(
                "multiInstanceUsernames", "alice",
                "multiInstanceUserIds", List.of("user-2", "alice"),
                "multiInstanceGroupCodes", "finance",
                "multiInstanceGroupIds", "group-2",
                "multiInstanceRoleCodes", "ROLE_MANAGER",
                "multiInstanceRoleIds", "role-2",
                "multiInstanceUsers", "carol,ROLE_AUDITOR"));

        assertEquals(
                List.of("alice", "user-2", "carol"),
                legacy.userKeys());
        assertEquals(
                List.of("finance", "group-2"),
                legacy.groupKeys());
        assertEquals(
                List.of("MANAGER", "role-2", "AUDITOR"),
                legacy.roleKeys());
        assertTrue(legacy.effective());
        assertFalse(legacy.resolver());
    }

    @Test
    void explicitVariableSourceIgnoresStaleResolverAndBlankLegacyFallsBack() {
        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        LinkedHashMap<String, Object> extraParams = new LinkedHashMap<>();
        extraParams.put("nullable", null);
        config.put("collectionSource", "variable");
        config.put("collectionResolverCode", "staleResolver");
        config.put("collectionExtraParams", extraParams);
        config.put("multiInstanceUserIds", List.of("", "   "));

        var legacy = LegacyMultiInstanceAssignmentParser.parse(config);

        assertTrue(legacy.declared());
        assertFalse(legacy.resolver());
        assertFalse(legacy.effective());
        assertEquals(null, legacy.resolverExtraParams().get("nullable"));
    }

    @Test
    void unionsAssigneeAndMultiInstanceDocumentsButVersionTwoIgnoresFallback() {
        Map<String, Object> primary = Map.of(
                "multiInstanceUsernames", "alice,bob",
                "multiInstanceUserIds", "bob,carol",
                "multiInstanceUsers", "dave,ROLE_AUDITOR");
        Map<String, Object> fallback = Map.of(
                "multiInstanceUsernames", "carol,erin",
                "multiInstanceUserIds", "frank,alice",
                "multiInstanceUsers", "grace,ROLE_REVIEWER");

        var legacy = LegacyMultiInstanceAssignmentParser.parse(
                primary, fallback);
        Map<String, Object> merged =
                LegacyMultiInstanceAssignmentParser.mergeConfigs(
                        primary, fallback);

        assertEquals(
                List.of(
                        "alice",
                        "bob",
                        "carol",
                        "erin",
                        "frank",
                        "dave",
                        "grace"),
                legacy.userKeys());
        assertEquals(List.of("AUDITOR", "REVIEWER"), legacy.roleKeys());
        assertEquals(
                legacy.userKeys(),
                merged.get("multiInstanceUsernames"));

        Map<String, Object> versionTwo =
                LegacyMultiInstanceAssignmentParser.mergeConfigs(
                        Map.of(
                                "assignmentConfigVersion", 2,
                                "assigneeType", "user",
                                "assigneeValue", "owner"),
                        fallback);
        assertFalse(versionTwo.containsKey("multiInstanceUsernames"));
        assertFalse(versionTwo.containsKey("multiInstanceUsers"));
        assertEquals("owner", versionTwo.get("assigneeValue"));
    }

    @Test
    void effectiveResolverCodeKeepsLegacyStaticSourceAheadOfStaleBaseResolver() {
        Map<String, Object> mixedLegacy = Map.of(
                "multiInstanceUsernames", List.of("alice"),
                "assigneeType", "interface",
                "resolverCode", "entityUserReferenceField");

        assertEquals(
                "",
                LegacyMultiInstanceAssignmentParser
                        .effectiveResolverCode(mixedLegacy));
        assertEquals(
                "entityUserReferenceField",
                LegacyMultiInstanceAssignmentParser.effectiveResolverCode(
                        Map.of(
                                "assignmentConfigVersion", 2,
                                "multiInstanceUsernames", List.of("alice"),
                                "assigneeType", "interface",
                                "resolverCode",
                                "entityUserReferenceField")));
    }

    @Test
    void legacyFieldsOnlyOverrideBaseResolverOnActualMultiInstanceSource() {
        Map<String, Object> config = Map.of(
                "collectionSource", "resolver",
                "collectionResolverCode", "legacyResolver",
                "collectionExtraParams", Map.of("fieldCode", "legacyField"),
                "assigneeType", "resolver",
                "resolverCode", "baseResolver",
                "extraParams", Map.of("fieldCode", "baseField"));

        var multiInstance = LegacyMultiInstanceAssignmentParser
                .effectiveResolver(config, true);
        var ordinary = LegacyMultiInstanceAssignmentParser
                .effectiveResolver(config, false);

        assertEquals("legacyResolver", multiInstance.resolverCode());
        assertEquals("legacyField",
                multiInstance.extraParams().get("fieldCode"));
        assertTrue(multiInstance.legacy());
        assertEquals("baseResolver", ordinary.resolverCode());
        assertEquals("baseField", ordinary.extraParams().get("fieldCode"));
        assertFalse(ordinary.legacy());
    }
}
