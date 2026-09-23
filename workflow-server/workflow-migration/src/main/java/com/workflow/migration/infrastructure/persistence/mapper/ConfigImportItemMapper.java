package com.workflow.migration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置导入条目 Mapper。
 *
 * <p>提供 config_import_item 表的基础 CRUD 能力。</p>
 */
@Mapper
public interface ConfigImportItemMapper extends BaseMapper<ConfigImportItem> {

    /**
     * 持久化重新分析结果，并显式允许清空过期错误信息。
     *
     * @param item 已完成比较和依赖分析的导入条目
     * @return 影响行数
     */
    default int updateAnalysisResult(ConfigImportItem item) {
        // 显式 set 保留 null 的清空语义，重新分析后不能沿用上次的目标版本或错误信息。
        return update(null, Wrappers.<ConfigImportItem>lambdaUpdate()
                .set(ConfigImportItem::getTargetBeforeVersion, item.getTargetBeforeVersion())
                .set(ConfigImportItem::getTargetBeforeHash, item.getTargetBeforeHash())
                .set(ConfigImportItem::getComparisonStatus, item.getComparisonStatus())
                .set(ConfigImportItem::getMappingStatus, item.getMappingStatus())
                .set(ConfigImportItem::getErrorMessage, item.getErrorMessage())
                .set(ConfigImportItem::getUpdatedAt, item.getUpdatedAt())
                .eq(ConfigImportItem::getId, item.getId()));
    }
}
