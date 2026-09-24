package com.workflow.process.task.application;

import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessTaskCancellationTest {
    @Mock ProcessTaskMapper mapper;
    @InjectMocks ProcessTaskService service;

    @Test void cancellingAProcessPreservesCompletedApprovals() {
        var done = new ProcessTask(); done.setId(1L); done.setStatus(ProcessTask.STATUS_DONE);
        var todo = new ProcessTask(); todo.setId(2L); todo.setStatus(ProcessTask.STATUS_TODO);
        when(mapper.selectByProcessInstance("process")).thenReturn(List.of(done, todo));
        service.deleteTasksByProcessInstance("process");
        verify(mapper).deleteById(2L);
        verify(mapper, never()).deleteById(1L);
    }
}
