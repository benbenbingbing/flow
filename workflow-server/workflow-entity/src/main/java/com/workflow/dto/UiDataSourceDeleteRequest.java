package com.workflow.dto;

import lombok.Data;

/**
 * UI 数据源删除请求。
 */
@Data
public class UiDataSourceDeleteRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
}
