package com.workflow.process.variable;

import com.workflow.contracts.process.model.*;
import com.workflow.contracts.process.task.model.*;
import com.workflow.contracts.process.variable.model.ProcessVariablePatch;
import com.workflow.process.variable.application.ProcessVariableReader;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignUserMapper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 公共值模型的边界以及技术失败不回退的约定，不以实现内部私有方法为断言对象。 */
class ProcessVariableContractTest {
    @Test
    void malformedCommandsAndPageRangesFailBeforeRuntimeAccess() {
        assertThrows(IllegalArgumentException.class, () -> new CommandMeta(" "));
        assertThrows(IllegalArgumentException.class, () -> new ProcessPageRequest(0, 20, null));
        assertThrows(IllegalArgumentException.class, () -> new ProcessPageRequest(1, 101, null));
        assertThrows(IllegalArgumentException.class, () -> new ProcessPageRequest(Integer.MAX_VALUE, 100, null));
        assertThrows(IllegalArgumentException.class, () -> ProcessValues.range(Instant.EPOCH, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new ProcessVariablePatch(new CommandMeta("r"), "p", Map.of("a", 1), Set.of("a")));
        assertThrows(IllegalArgumentException.class, () -> new TransferTaskCommand(new CommandMeta("r"), "t", " ", null));
        assertThrows(IllegalArgumentException.class, () -> new ReturnTaskCommand(new CommandMeta("r"), "t", "", null));
        assertThrows(IllegalArgumentException.class, () -> new CompleteTaskCommand(new CommandMeta("r"), "t", "", null));
    }

    @Test
    void inputIsDetachedAndUnsupportedValuesFailExplicitly() {
        List<Object> nested = new ArrayList<>(Arrays.asList("first", null));
        Map<String, Object> input = new LinkedHashMap<>(); input.put("nested", nested); input.put("time", new Date(0));
        var patch = new ProcessVariablePatch(new CommandMeta("r"), "p", input, Set.of("z", "a"));
        nested.add("later"); input.put("injected", "later");
        assertEquals(2, ((List<?>) patch.setValues().get("nested")).size());
        assertFalse(patch.setValues().containsKey("injected"));
        assertEquals(Instant.EPOCH, patch.setValues().get("time"));
        assertEquals(List.of("a", "z"), new ArrayList<>(patch.removeNames()));
        assertThrows(IllegalArgumentException.class, () -> ProcessValues.snapshotValue(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> ProcessValues.snapshotValue(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> ProcessValues.snapshotValue(new Object()));
        List<Object> cyclic = new ArrayList<>(); cyclic.add(cyclic);
        assertThrows(IllegalArgumentException.class, () -> ProcessValues.snapshotValue(cyclic));
    }

    @Test
    void runtimeFailureCannotBeConvertedToHistoricalResult() {
        var runtime = mock(RuntimeService.class, RETURNS_DEEP_STUBS);
        var history = mock(HistoryService.class);
        var failure = new IllegalStateException("database failure");
        when(runtime.createProcessInstanceQuery().processInstanceId("p").singleResult()).thenThrow(failure);
        var reader = new ProcessVariableReader(runtime, mock(TaskService.class), history, mock(ProcessTaskAddSignUserMapper.class));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> reader.process("p",
                com.workflow.contracts.process.variable.model.VariableReadMode.RUNTIME_OR_HISTORY)));
        verifyNoInteractions(history);
    }
}
