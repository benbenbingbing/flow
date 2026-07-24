package com.workflow.dto;

import lombok.Data;

/**
 * 实体列表场景保存请求。
 */
@Data
public class EntityListSceneSaveRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
    /** 场景编码 */
    private String sceneCode;
    /** 排序序号 */
    private Integer sortOrder;
}
