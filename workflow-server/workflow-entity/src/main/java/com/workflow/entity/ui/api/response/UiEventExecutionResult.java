package com.workflow.entity.ui.api.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UI 事件执行结果。
 */
@Data
public class UiEventExecutionResult {

    private Object data;
    private String message;
    private boolean defaultExecuted;
    private boolean replaced;
    private List<Map<String, Object>> effects = new ArrayList<>();
    private List<Map<String, Object>> trace = new ArrayList<>();
}
