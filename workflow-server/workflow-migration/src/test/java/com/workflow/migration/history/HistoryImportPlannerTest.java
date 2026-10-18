package com.workflow.migration.history;

import com.workflow.contracts.history.*;
import com.workflow.contracts.history.HistoryImportModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoryImportPlannerTest {
    final EntityHistoryImportPort entities = mock(EntityHistoryImportPort.class);
    final ProcessArchivePort archives = mock(ProcessArchivePort.class);
    final HistoryImportPlanner planner = new HistoryImportPlanner(entities, archives);
    final EntitySpec main = new EntitySpec("main", "expense", true, Map.of("supplier", "supplier"));
    final EntitySpec child = new EntitySpec("items", "item", true, Map.of("parent", "main"));
    final EntitySpec supplier = new EntitySpec("supplier", "supplier", false, Map.of());
    final EntityRow parentRow = new EntityRow("main", "0001", "z-business", Map.of("supplier", "S01"));
    final EntityRow childRow = new EntityRow("items", "001", "z-business", Map.of("parent", "0001"));
    final EntityRow supplierRow = new EntityRow("supplier", "S01", "a-shared", Map.of("name", "供应商"));

    @BeforeEach void setup() {
        when(entities.schema("expense")).thenReturn(new EntitySchema("expense", "报销", "v1",
                List.of(new Field("supplier", "供应商", "REFERENCE", false, "supplier")),
                List.of(new Relation("明细", "item", "parent", true, true, false))));
        when(entities.schema("item")).thenReturn(new EntitySchema("item", "明细", "v1",
                List.of(new Field("parent", "父记录", "STRING", true, null)), List.of()));
        when(entities.schema("supplier")).thenReturn(new EntitySchema("supplier", "供应商", "v1",
                List.of(new Field("name", "名称", "STRING", true, null)), List.of()));
        when(entities.normalize(any(), anyMap())).thenAnswer(call -> call.getArgument(1));
        when(archives.template("v1")).thenReturn(new ProcessTemplate("v1", "审批", 1, "<xml/>", Map.of("review", "审批")));
    }
    Plan plan() { return new Plan("导入", "legacy", List.of(main, child, supplier), "v1", Map.of("旧审批", "review"), Map.of()); }
    Input input(List<EntityRow> rows) { return new Input(rows, List.of(), List.of()); }
    InstanceRow instance(int count) { return new InstanceRow("P1", "main", "0001", "历史报销", "张三", "2020-01-01 12:00:00", "2020-01-02 12:00:00", "APPROVED", count); }
    EventRow event(String id) { return new EventRow(id, "P1", "旧审批", "审批人", "同意", "意见", "2020-01-02 10:00:00", 1); }

    @Test void sortsSharedEntityBeforeParentAndChildEvenWhenSheetsAreReversed() {
        var result = planner.validate(plan(), input(List.of(childRow, parentRow, supplierRow)));
        assertEquals(List.of("a-shared", "z-business"), result.groupOrder());
        assertEquals(List.of(HistoryImportPlanner.key(parentRow), HistoryImportPlanner.key(childRow)), result.groups().get("z-business"));
        assertNull(result.template(), "纯业务实体导入不需要流程图");
    }
    @Test void requiresCompositionChildrenInSameGroup() {
        var separated = new EntityRow("items", "001", "other", childRow.values());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> planner.validate(plan(), input(List.of(separated, parentRow, supplierRow)))).getMessage().contains("同一提交组"));
    }
    @Test void permitsReferenceToPreviouslyImportedSharedEntity() {
        when(entities.findSource("legacy", "supplier", "S01")).thenReturn(new SourceRecord("supplier", "new-supplier", "hash"));
        assertEquals(1, planner.validate(plan(), input(List.of(parentRow, childRow))).groups().size());
    }
    @Test void rejectsMissingReferenceAndDuplicateSourceIdentity() {
        assertThrows(IllegalArgumentException.class, () -> planner.validate(plan(), input(List.of(parentRow, childRow))));
        assertThrows(IllegalArgumentException.class, () -> planner.validate(plan(), input(List.of(parentRow, parentRow, supplierRow))));
    }
    @Test void rejectsBothRecordAndGroupDependencyCycles() {
        assertThrows(IllegalArgumentException.class, () -> HistoryImportPlanner.topological(Map.of("a", Set.of("b"), "b", Set.of("a"))));
    }
    @Test void keepsRepeatedNodeEventsAndChecksDeclaredCount() {
        var result = planner.validate(plan(), new Input(List.of(parentRow, childRow, supplierRow), List.of(instance(2)), List.of(event("E1"), event("E2"))));
        assertEquals(2, result.input().events().size());
        assertThrows(IllegalArgumentException.class, () -> planner.validate(plan(), new Input(List.of(parentRow, childRow, supplierRow), List.of(instance(1)), List.of(event("E1"), event("E2")))));
    }
    @Test void rejectsRunningInstancesAndUnmappedNodes() {
        var running = new InstanceRow("P1", "main", "0001", "流程", "人", "", "", "RUNNING", 0);
        assertThrows(IllegalArgumentException.class, () -> planner.validate(plan(), new Input(List.of(parentRow, supplierRow), List.of(running), List.of())));
        var noMap = new Plan("批次", "legacy", plan().entities(), "v1", Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> planner.validate(noMap, new Input(List.of(parentRow, supplierRow), List.of(instance(1)), List.of(event("E1")))));
    }
    @Test void acceptsTimelineOnlyAndRejectsCaseCollidingSheets() {
        var timeline = new Plan("批次", "legacy", plan().entities(), "v1", Map.of("旧审批", "TIMELINE_ONLY"), Map.of());
        assertEquals(1, planner.validate(timeline, new Input(List.of(parentRow, supplierRow), List.of(instance(1)), List.of(event("E1")))).input().events().size());
        assertThrows(IllegalArgumentException.class, () -> HistoryImportPlanner.validatePlan(new Plan("批次", "legacy", List.of(main, new EntitySpec("MAIN", "other", true, Map.of())), "", Map.of(), Map.of())));
    }
    @Test void sourceHashesAreLengthDelimitedAndCaseSensitive() {
        assertNotEquals(HistoryIdentity.hash("a:b", "c"), HistoryIdentity.hash("a", "b:c"));
        assertNotEquals(HistoryIdentity.hash("legacy", "001"), HistoryIdentity.hash("legacy", "1"));
        assertNotEquals(HistoryIdentity.hash("legacy", "A"), HistoryIdentity.hash("legacy", "a"));
    }
}
