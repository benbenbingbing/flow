package com.workflow.contracts.process.open;

import java.util.Objects;

public record OpenProcessCancelCommand(
        String processInstanceId,
        String reason,
        OpenApplicationActor actor) {

    public OpenProcessCancelCommand {
        Objects.requireNonNull(processInstanceId, "processInstanceId");
        Objects.requireNonNull(actor, "actor");
    }
}
