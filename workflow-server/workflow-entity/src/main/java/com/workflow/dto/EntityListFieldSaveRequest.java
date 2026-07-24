package com.workflow.dto;

import com.workflow.entity.EntityListField;
import lombok.Data;

import java.util.Set;

/**
 * 实体列表字段保存请求。
 */
@Data
public class EntityListFieldSaveRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
    /** 待保存的列表字段配置 */
    private EntityListField field;
    /** 需要清空的字段集合（局部更新时置空指定字段） */
    private Set<String> clearFields;
}
