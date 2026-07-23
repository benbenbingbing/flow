package com.workflow.dto.migration;

import lombok.Data;

import java.util.List;

/**
 * 配置导入发布请求。
 *
 * <p>发布导入批次时可指定需要发布的条目ID列表；为空表示发布批次内全部条目。</p>
 */
@Data
public class ConfigImportPublishRequest {

    private List<String> itemIds;   // 待发布的导入条目ID列表(为空表示全部)
}
