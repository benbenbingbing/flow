package com.workflow.contracts.process;

import java.util.Collection;
import java.util.Map;

public interface ProcessCatalogPort {

    Map<String, String> findNamesByIds(Collection<String> processIds);
}
