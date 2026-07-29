package com.workflow.contracts.process.open;

import java.util.List;

public interface OpenProcessRuntimePort {

    OpenProcessView start(OpenProcessStartCommand command);

    OpenProcessView get(
            String processInstanceId,
            OpenApplicationActor actor);

    List<OpenTaskView> listActiveTasks(
            String processInstanceId,
            int offset,
            int limit,
            OpenApplicationActor actor);

    OpenMessageCorrelationResult correlate(
            OpenMessageCorrelationCommand command);
}
