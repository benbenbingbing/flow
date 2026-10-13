package com.workflow.entity.excel.model;

/** 执行受理时解析的方案；defaultHash 仅覆盖本次方向，指定方案使用空串。 */
public record ExcelResolvedScheme(String entityId, String entityCode, String schemeCode,
                                  ExcelSchemeDocument document, String contentHash,
                                  String defaultHash, String writeFormId) {}
