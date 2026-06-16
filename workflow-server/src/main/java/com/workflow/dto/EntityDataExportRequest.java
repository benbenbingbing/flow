package com.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 实体数据导出请求
 */
@Data
public class EntityDataExportRequest {

    /**
     * 导出类型：ALL-全部，SELECTED-选中
     */
    private String exportType;

    /**
     * 选中数据ID列表
     */
    private List<String> ids;

    /**
     * 列表标识
     */
    private String listKey;

    /**
     * 查询条件
     */
    private Map<String, Object> condition;

    /**
     * 导出按钮对应的权限码
     */
    private String perm;
}
