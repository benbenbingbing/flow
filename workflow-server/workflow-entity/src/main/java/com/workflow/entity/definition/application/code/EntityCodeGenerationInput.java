package com.workflow.entity.definition.application.code;

import java.util.Map;

/** 平台内部写入输入，storageData 必须已移除嵌套关系令牌；不作为对外 SPI 暴露。 */
public record EntityCodeGenerationInput(String entityCode, String recordId, Map<String, Object> storageData,
        String parentEntityCode, String parentRecordId, String submissionPath, String suppliedCode) { }
