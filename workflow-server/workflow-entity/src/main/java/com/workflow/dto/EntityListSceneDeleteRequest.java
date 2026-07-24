package com.workflow.dto;

import lombok.Data;

/**
 * 实体列表场景删除请求。
 */
@Data
public class EntityListSceneDeleteRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
}
