package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.port.system.CurrentActorPort;
import com.workflow.port.entity.context.ui.UiRuntimeResolutionContext;
import com.workflow.port.system.SysOrganizationPort;
import com.workflow.port.system.SysUserPort;
import com.workflow.port.system.model.identity.CurrentActor;
import com.workflow.port.system.model.identity.IdentityUser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FormContextDefaultsTest {
    private final CurrentActorPort actor = mock(CurrentActorPort.class);
    private final SysUserPort users = mock(SysUserPort.class);
    private final FormContextDefaults defaults = new FormContextDefaults(
            actor, users, mock(SysOrganizationPort.class), new JsonDocumentCodec(new ObjectMapper()),
            Clock.fixed(Instant.parse("2026-11-26T16:00:01Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void projectsOnlyReferencedValuesForTheRequestActor() {
        EntityForm form = form(field("applicant", "currentUser.userId"), field("nickname", "currentUser.nickname"));
        when(actor.current()).thenReturn(new CurrentActor("10001", "alice"), new CurrentActor("10002", "bob"));
        when(users.findUsersByIds(List.of("10001"))).thenReturn(List.of(
                new IdentityUser("10001", "alice", "张三", "30001", "20010")));
        when(users.findUsersByIds(List.of("10002"))).thenReturn(List.of(
                new IdentityUser("10002", "bob", "李四", "30002", null)));

        assertEquals(Map.of("currentUser.userId", "10001", "currentUser.nickname", "张三"), defaults.valuesFor(form));
        assertEquals(Map.of("currentUser.userId", "10002", "currentUser.nickname", "李四"), defaults.valuesFor(form));
        verify(users).findUsersByIds(List.of("10001"));
        verify(users).findUsersByIds(List.of("10002"));
    }

    @Test
    void fillsOnlyMissingFieldsAndUsesAuthenticatedId() {
        EntityForm form = form(field("owner", "currentUser.userId"), field("reviewer", "currentUser.userId"),
                field("contact", "currentUser.userId"));
        when(actor.current()).thenReturn(new CurrentActor("10001", "alice"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reviewer", "10008");
        data.put("contact", null);

        defaults.apply(form, data);

        assertEquals("10001", data.get("owner"));
        assertEquals("10008", data.get("reviewer"));
        assertNull(data.get("contact"));
    }

    @Test
    void validatesKeysAndEntryWithoutRestrictingTheTargetFieldType() {
        assertDoesNotThrow(() -> FormContextDefaults.validate("${ctx.currentUser.userId}"));
        assertDoesNotThrow(() -> FormContextDefaults.validate("\" ${ctx.example}\""));
        assertDoesNotThrow(() -> FormContextDefaults.validate("COALESCE(${ctx.currentUser.nickname}, \"匿名\")"));
        assertThrows(IllegalArgumentException.class, () -> FormContextDefaults.validate("${ctx.currentUser.unknown}"));
        assertThrows(IllegalArgumentException.class, () -> FormContextDefaults.validate("${ctx.currentTask.id}"));
    }

    @Test
    void usesTheSameBusinessClockForDateAndTimeOnReadAndSubmit() {
        EntityForm form = form(field("day", "currentDate"), field("time", "currentTime"));
        assertDoesNotThrow(() -> FormContextDefaults.validate("${ctx.currentDate}"));
        assertDoesNotThrow(() -> FormContextDefaults.validate("${ctx.currentTime}"));

        assertEquals(Map.of("currentDate", "2026-11-27", "currentTime", "2026-11-27 00:00:01"), defaults.valuesFor(form));
        Map<String, Object> submitted = new LinkedHashMap<>();
        submitted.put("day", "");
        defaults.apply(form, submitted);
        assertEquals(Map.of("day", "", "time", "2026-11-27 00:00:01"), submitted);
        verifyNoInteractions(actor, users);
    }

    @Test
    void recalculatesThePublishedValueFromTheAuthorizedTaskAndRejectsDisabledChanges() {
        EntityFormNode amount = field("amount", "currentDate");
        amount.setPropsDocument("""
                {"fieldCode":"amount","fieldType":"INTEGER"}
                """);
        EntityFormNode result = field("result", "currentDate");
        result.setPropsDocument("""
                {"fieldCode":"result","fieldType":"INTEGER","componentProps":{"linkageRules":{
                  "valueFormula":"IF(${ctx.currentTask.nodeKey} == 'review', ${amount} * 2, 0)",
                  "contextCalculation":{"expressionVersion":1,"modes":["approve"]}}}}
                """);
        EntityFormNode note = field("note", "currentDate");
        note.setPropsDocument("""
                {"fieldCode":"note","fieldType":"STRING","componentProps":{"linkageRules":{"disabledConditionConfig":{"version":1,
                  "root":{"type":"GROUP","logic":"AND","children":[{"type":"CONDITION","property":"ctx.currentProcessNode.code",
                    "operator":"==","value":"finance_review"}]}}}}}
                """);
        EntityForm form = form(amount, result, note);
        var mapper = new ObjectMapper();
        var rules = new FormContextRules(new JsonDocumentCodec(mapper), new PublishedFormConditionEvaluator(mapper), mock(EntityFieldMapper.class));
        var entry = UiRuntimeResolutionContext.activeTask("release", "review", "todo-1", "flow-1", "expense", "record-1")
                .withFormValues(Map.of("currentTask.nodeKey", "review", "currentProcessNode.code", "finance_review"));
        var values = defaults.runtime(form, entry, "approve");
        rules.validate(form);
        var submission = rules.new Submission(form, "approve", values, Map.of("note", "原值"), Map.of());
        Map<String, Object> patch = new LinkedHashMap<>(Map.of("amount", 8, "result", 999));
        submission.discardClientCalculations(patch);
        submission.finish(patch);
        assertEquals(16, patch.get("result"));
        patch.put("note", "尝试修改");
        assertThrows(BusinessConflictException.class, () -> submission.finish(patch));
    }

    private static EntityForm form(EntityFormNode... nodes) {
        EntityForm form = new EntityForm();
        form.setNodes(List.of(nodes));
        return form;
    }

    private static EntityFormNode field(String fieldCode, String key) {
        EntityFormNode node = new EntityFormNode();
        node.setId(fieldCode + "-node");
        node.setNodeType("FIELD");
        node.setBindingType("ENTITY_FIELD");
        node.setBindingRef(fieldCode);
        node.setPropsDocument("""
                {"fieldCode":"%s","defaultValueMode":"formula","defaultFormula":"${ctx.%s}"}
                """.formatted(fieldCode, key));
        return node;
    }
}
