package com.workflow.dto;

import lombok.Data;

/**
 * 表单节点删除请求。
 */
@Data
public class EntityFormNodeDeleteRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
}
