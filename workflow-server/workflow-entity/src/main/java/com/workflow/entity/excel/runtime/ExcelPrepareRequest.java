package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Map;

/** 默认按钮只提交来源和选择集；操作及方案始终从服务端标准按钮解析。 */
public record ExcelPrepareRequest(
        @JsonAlias("configType") String ownerType,
        @JsonAlias("configId") String ownerId,
        @JsonAlias("targetKey") String buttonCode,
        String entityCode, String listKey, String configHash,
        Map<String, Object> runtimeContext, String recordId,
        @JsonAlias("selectedIds") List<String> recordIds,
        @JsonAlias("condition") Map<String, Object> filters) { }
