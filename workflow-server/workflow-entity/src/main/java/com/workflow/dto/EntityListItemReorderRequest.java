package com.workflow.dto;

import lombok.Data;

/**
 * 实体列表项拖拽排序请求。
 * 通过指定前后相邻项 ID 实现插入式排序，并基于期望修订号做乐观并发控制。
 */
@Data
public class EntityListItemReorderRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
    /** 排序后目标项的前一个列表项 ID，无前项时为 null */
    private String previousId;
    /** 排序后目标项的后一个列表项 ID，无后项时为 null */
    private String nextId;
}
