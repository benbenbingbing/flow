package com.workflow.contracts.process.open;

import java.util.Collection;
import java.util.List;

public interface OpenProcessCatalogPort {

    List<OpenProcessDefinition> listPublished(
            Collection<String> processKeys,
            OpenApplicationActor actor);
}
