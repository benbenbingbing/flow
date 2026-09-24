package com.workflow.entity.permission.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.contracts.process.port.ProcessTaskAccessPort;
import com.workflow.contracts.process.port.ProcessTaskAccessPort.RecordCoordinates;
import com.workflow.contracts.process.port.ProcessTaskAccessPort.TaskCapability;
import com.workflow.entity.data.api.response.EntityDataDTO;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 批量能力只供本次展示；同一页多按钮复用，离开作用域及提交上下文仍执行实时查询。 */
class TaskCapabilityDisplayBatchTest {
    @Test
    void pageUsesOneLazyBatchAndSubmissionsAlwaysRecheck() {
        var port = mock(ProcessTaskAccessPort.class);
        var lookup = new CurrentProcessTaskAssigneeLookup(port);
        var user = user("alice");
        var rows = java.util.stream.IntStream.range(0, 50).mapToObj(index -> row("r" + index)).toList();
        var coordinates = rows.stream().map(row -> new RecordCoordinates("expense", row.getId(), "p1")).toList();
        var values = new HashMap<RecordCoordinates, TaskCapability>();
        coordinates.forEach(key -> values.put(key, new TaskCapability("task-" + key.entityDataId(), "审核", false)));
        when(port.findCapabilities("alice", coordinates)).thenReturn(values);
        try (var scope = lookup.openDisplayBatch(rows, user)) {
            verifyNoInteractions(port);
            for (var row : rows) {
                for (int button = 0; button < 5; button++) {
                    assertEquals(Optional.of("task-" + row.getId()), lookup.findActionableTaskId(row, user));
                    assertEquals(Optional.of("审核"), lookup.findActionableTaskName(row, user, "task-" + row.getId()));
                    assertFalse(lookup.isCurrentAssignee(row, user));
                }
            }
            // 精确审批上下文即使在展示作用域中调用，也不允许使用展示快照放行。
            assertTrue(lookup.findActionableTaskContext(rows.get(0), user, "task-r0").isEmpty());
        }
        verify(port, times(1)).findCapabilities("alice", coordinates);
        verify(port).findActionableTaskContext("alice", "task-r0", "expense", "r0", "p1");
        when(port.findActionableTaskId("alice", "expense", "r0", "p1")).thenReturn(Optional.of("fresh"));
        assertEquals(Optional.of("fresh"), lookup.findActionableTaskId(rows.get(0), user));
        verify(port).findActionableTaskId("alice", "expense", "r0", "p1");
    }

    @Test
    void emptyCapabilitiesFailClosedAndScopeDoesNotLeakOnExceptionOrAcrossUsers() {
        var port = mock(ProcessTaskAccessPort.class);
        var lookup = new CurrentProcessTaskAssigneeLookup(port);
        var row = row("r1");
        var alice = user("alice");
        when(port.findActionableTaskId("bob", "expense", "r1", "p1")).thenReturn(Optional.of("bobs-task"));
        assertThrows(IllegalStateException.class, () -> {
            try (var scope = lookup.openDisplayBatch(List.of(row), alice)) {
                assertTrue(lookup.findActionableTaskId(row, alice).isEmpty());
                assertFalse(lookup.isCurrentAssignee(row, alice));
                assertEquals(Optional.of("bobs-task"), lookup.findActionableTaskId(row, user("bob")));
                throw new IllegalStateException("display failed");
            }
        });
        lookup.findActionableTaskId(row, alice);
        verify(port).findActionableTaskId("alice", "expense", "r1", "p1");
        verify(port).findActionableTaskId("bob", "expense", "r1", "p1");
    }

    private SysUser user(String id) { var user = new SysUser(); user.setId(id); return user; }
    private EntityDataDTO row(String id) {
        var row = new EntityDataDTO(); row.setId(id); row.setEntityCode("expense"); row.setProcessInstanceId("p1"); return row;
    }
}
