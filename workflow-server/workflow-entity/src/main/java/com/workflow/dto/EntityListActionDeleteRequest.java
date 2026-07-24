package com.workflow.dto;

import lombok.Data;

/**
 * 实体列表按钮（操作项）删除请求。
 */
@Data
public class EntityListActionDeleteRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
}
