package com.workflow.dto.migration;

import lombok.Data;

import java.util.List;

/**
 * 配置环境映射保存请求。
 *
 * <p>批量保存源环境到目标环境的资源编码映射列表，供导入分析阶段使用。</p>
 */
@Data
public class ConfigEnvironmentMappingRequest {

    private List<MappingItem> mappings;   // 映射条目列表

    /**
     * 单条环境映射项。
     */
    @Data
    public static class MappingItem {
        private String sourceType;                 // 资源类型(USER/ROLE/DEPT/ENTITY等)
        private String sourceKey;                  // 源环境编码
        private String targetKey;                  // 目标环境对应编码
        private String description;                // 映射说明
        private Boolean enabled = Boolean.TRUE;    // 是否启用
    }
}
