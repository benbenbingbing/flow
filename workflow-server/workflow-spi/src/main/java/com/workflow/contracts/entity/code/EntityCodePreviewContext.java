package com.workflow.contracts.entity.code;

import java.time.LocalDateTime;

/** 配置预览没有真实记录 ID 和业务数据，生成器应返回格式样例或声明不支持预览。 */
public record EntityCodePreviewContext(String entityCode, LocalDateTime generationTime) { }
