package com.workflow.dto;

import lombok.Data;

/**
 * 表单节点拖拽排序请求。
 * 通过指定父节点及前后相邻节点 ID 实现插入式排序。
 */
@Data
public class EntityFormNodeReorderRequest {

    /** 客户端读取到的草稿修订号，用于乐观并发控制 */
    private Integer expectedRevision;
    /** 目标节点的父节点 ID */
    private String parentId;
    /** 排序后目标节点的前一个节点 ID，无前项时为 null */
    private String previousNodeId;
    /** 排序后目标节点的后一个节点 ID，无后项时为 null */
    private String nextNodeId;
}
