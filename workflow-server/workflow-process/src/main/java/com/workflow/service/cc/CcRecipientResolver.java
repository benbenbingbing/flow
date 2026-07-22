package com.workflow.service.cc;

import java.util.List;
import java.util.Map;

public interface CcRecipientResolver {
    String code();

    List<String> resolve(CcRuntimeContext context, Map<String, Object> parameters);
}
