package com.workflow.dto;

import lombok.Data;

import java.util.Map;

/**
 * UI 组件模板保存请求。
 */
@Data
public class UiComponentTemplateSaveRequest {

    /** 模板 ID（更新时传入） */
    private String id;
    /** 模板编码 */
    private String templateKey;
    /** 模板名称 */
    private String templateName;
    /** 模板类型 */
    private String templateType;
    /** 模板描述 */
    private String description;
    /** 模板快照内容（JSON 文档） */
    private Map<String, Object> snapshot;
}
