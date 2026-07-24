package com.workflow.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体列表运行时上下文。
 * 用于携带关联来源、关联键以及运行时参数，供数据查询与权限解析使用。
 */
@Data
public class EntityListRuntimeContextDTO {
    /** 来源实体编码（如子表/关联实体场景下的父实体） */
    private String sourceEntityCode;
    /** 来源记录 ID */
    private String sourceRecordId;
    /** 关联键（用于父子数据联动） */
    private String relationKey;
    /** 运行时参数集合 */
    private Map<String, Object> parameters = new LinkedHashMap<>();
}
